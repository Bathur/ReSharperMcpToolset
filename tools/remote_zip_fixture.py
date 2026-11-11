# Copyright (C) 2026 Bathur.
# Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
# See LICENSE and LICENSING.md in the public source root.

"""Small loopback-only HTTP fixtures for the real PowerShell ZIP downloader."""

import argparse
import base64
import hashlib
import io
import json
import re
import select
import struct
import threading
import time
import zipfile
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ENTRY = "lib/rd/a.jar"
MAX_BODY = 2 * 1024 * 1024


def archive(entries, compression=zipfile.ZIP_STORED):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as output:
        for name, content in entries:
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            item.compress_type = compression
            output.writestr(item, content)
    return stream.getvalue()


padding = ("padding.bin", bytes(range(256)) * 384)
sources = {
    "stored": ([padding, (ENTRY, b"AAAAA"), ("lib/rd/b.jar", b"BBBBB")], zipfile.ZIP_STORED),
    "reordered": ([padding, ("lib/rd/b.jar", b"BBBBB"), (ENTRY, b"AAAAA")], zipfile.ZIP_STORED),
    "deflated": ([(ENTRY, b"DEFLATED fixture content\n" * 100)], zipfile.ZIP_DEFLATED),
    "case_collision": ([("lib/rd/A.jar", b"UPPER"), (ENTRY, b"lower")], zipfile.ZIP_STORED),
    "large_directory": ([(ENTRY, b"AAAAA")] + [(f"padding/entry-{index:04d}.txt", b"") for index in range(1200)], zipfile.ZIP_STORED),
}
archives = {name: archive(entries, method) for name, (entries, method) in sources.items()}
metadata = {}
for name, (entries, _) in sources.items():
    data = archives[name]
    content = dict(entries)[ENTRY]
    # These are fixtures we just wrote with no comment, not a general ZIP reader.
    directory_size, directory_offset = struct.unpack_from("<II", data, len(data) - 10)
    metadata[name] = {
        "archive_size": len(data),
        "central_offset": directory_offset,
        "central_size": directory_size,
        "entry_size": len(content),
        "sha256": hashlib.sha256(content).hexdigest().upper(),
        "entry_base64": base64.b64encode(content).decode(),
        "central_base64": base64.b64encode(data[directory_offset:directory_offset + directory_size]).decode(),
    }

damaged = bytearray(archives["stored"])
with zipfile.ZipFile(io.BytesIO(damaged)) as source:
    item = source.getinfo(ENTRY)
    name_length, extra_length = struct.unpack_from("<HH", damaged, item.header_offset + 26)
    data_start = item.header_offset + 30 + name_length + extra_length
damaged[data_start] ^= 1
archives["damaged"] = bytes(damaged)
metadata["damaged"] = metadata["stored"]


class SparseArchive:
    """Serve bounded slices at true ZIP64 offsets without a multi-GiB fixture."""

    def __init__(self, size, segments):
        self.size = size
        self.segments = segments
        identity = hashlib.sha256()
        identity.update(struct.pack("<Q", size))
        for offset, content in segments:
            identity.update(struct.pack("<Q", offset))
            identity.update(content)
        self.etag_identity = identity.hexdigest()

    def __len__(self):
        return self.size

    def __getitem__(self, key):
        start, stop, step = key.indices(self.size)
        if step != 1 or stop - start > MAX_BODY:
            raise ValueError("Sparse archive reads must remain bounded byte ranges.")
        result = bytearray(stop - start)
        for offset, content in self.segments:
            overlap_start = max(start, offset)
            overlap_end = min(stop, offset + len(content))
            if overlap_start < overlap_end:
                result[overlap_start - start:overlap_end - start] = content[overlap_start - offset:overlap_end - offset]
        return bytes(result)


def zip64_fixture(name, *, fields=("offset",), wide=False, count_only=False, fault=None):
    # PKWARE APPNOTE 4.3.14-16 and 4.5.3: central ZIP64 fields occur only
    # for corresponding sentinels, in size/compressed-size/offset/disk order.
    content = b"ZIP64 fixture content\n" * 24
    encoded_name = ENTRY.encode()
    local_offset = (1 << 32) + 4096 if wide else 0
    local = struct.pack("<IHHHHHIIIHH", 0x04034B50, 45, 0, 0, 0, 0,
                        zlib.crc32(content), len(content), len(content), len(encoded_name), 0) + encoded_name + content
    values = {"size": len(content), "compressed": len(content), "offset": local_offset, "disk": 0}
    if fault == "extra_overflow":
        values["offset"] = 1 << 63
    elif fault == "entry_multidisk":
        values["disk"] = 1
    elif fault == "local_outside":
        values["offset"] = local_offset + len(local) + 4096
    extra_values = b"".join(struct.pack("<I" if field == "disk" else "<Q", values[field])
                            for field in ("size", "compressed", "offset", "disk") if field in fields)
    extra = struct.pack("<HH", 1, len(extra_values)) + extra_values
    if fault == "missing_extra":
        extra = b""
    elif fault == "truncated_extra":
        extra = struct.pack("<HH", 1, len(extra_values) - 1) + extra_values[:-1]
    elif fault == "duplicate_extra":
        extra += extra
    # Unknown well-formed extra fields before ZIP64 exercise field traversal.
    if extra:
        extra = struct.pack("<HH3s", 0xCAFE, 3, b"tag") + extra
    central = struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 45, 45, 0, 0, 0, 0,
                          zlib.crc32(content), 0xFFFFFFFF if "compressed" in fields else len(content),
                          0xFFFFFFFF if "size" in fields else len(content), len(encoded_name), len(extra), 0,
                          0xFFFF if "disk" in fields else 0, 0, 0,
                          0xFFFFFFFF if "offset" in fields else local_offset) + encoded_name + extra
    central_offset = local_offset + len(local)
    record_offset = central_offset + len(central)
    directory_offset = central_offset
    directory_size = len(central)
    record_size = 44
    disk = 0
    entries_on_disk = entries_total = 1
    if fault == "central_outside":
        directory_offset = record_offset + 1
    elif fault == "central_overflow":
        directory_offset = (1 << 64) - 1
    elif fault == "central_oversized":
        directory_size = (1 << 31)
    elif fault == "short_record":
        record_size = 43
    elif fault == "record_overflow":
        record_size = (1 << 64) - 1
    elif fault == "record_multidisk":
        disk = 1
    elif fault == "count_mismatch":
        entries_total = 2
    record = struct.pack("<IQHHIIQQQQ", 0x06064B50, record_size, 45, 45,
                         disk, 0, entries_on_disk, entries_total, directory_size, directory_offset)
    locator = struct.pack("<IIQI", 0x07064B50, 0, record_offset, 2 if fault == "locator_multidisk" else 1)
    if fault == "missing_locator":
        locator = b"\0" * len(locator)
    elif fault == "locator_outside":
        locator = struct.pack("<IIQI", 0x07064B50, 0, record_offset + 4096, 1)
    elif fault == "locator_overflow":
        locator = struct.pack("<IIQI", 0x07064B50, 0, 1 << 63, 1)
    end = struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, 0xFFFF, 0xFFFF,
                      len(central) if count_only else 0xFFFFFFFF,
                      central_offset if count_only else 0xFFFFFFFF, 0)
    ending = record + locator + end
    data = SparseArchive(record_offset + len(ending), [(local_offset, local), (central_offset, central), (record_offset, ending)])
    archives[name] = data
    metadata[name] = {
        "archive_size": len(data), "central_offset": central_offset, "central_size": len(central),
        "entry_size": len(content), "sha256": hashlib.sha256(content).hexdigest().upper(),
        "entry_base64": base64.b64encode(content).decode(), "central_base64": base64.b64encode(central).decode(),
    }


zip64_fixture("zip64_wide_offset", wide=True)
zip64_fixture("zip64_wide_compressed_offset", fields=("compressed", "offset"), wide=True)
zip64_fixture("zip64_all_fields", fields=("size", "compressed", "offset", "disk"))
zip64_fixture("zip64_count_only", fields=("size",), count_only=True)
for fault in ("missing_locator", "locator_multidisk", "locator_outside", "locator_overflow",
              "short_record", "record_overflow", "record_multidisk", "count_mismatch",
              "central_outside", "central_overflow", "central_oversized", "missing_extra",
              "truncated_extra", "duplicate_extra", "extra_overflow", "local_outside"):
    zip64_fixture("zip64_" + fault, fault=fault)
zip64_fixture("zip64_entry_multidisk", fields=("offset", "disk"), fault="entry_multidisk")


class State:
    def __init__(self):
        self.lock = threading.Lock()
        self.cases = {}

    def configure(self, value):
        case_id = value["id"]
        with self.lock:
            existing = self.cases.get(case_id)
            self.cases[case_id] = {
                "archive": value.get("archive", "stored"),
                "mode": value.get("mode", "valid"),
                "revision": value.get("revision", 1),
                "requests": existing["requests"] if existing and value.get("keep_stats") else [],
            }

    def snapshot(self, case_id):
        with self.lock:
            return json.loads(json.dumps(self.cases[case_id]))


state = State()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def json_response(self, value):
        content = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(content)

    def do_POST(self):
        if self.path != "/control":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 < length <= 4096:
            self.send_error(400)
            return
        state.configure(json.loads(self.rfile.read(length)))
        self.json_response({"ok": True})

    def do_HEAD(self):
        self.artifact(head=True)

    def do_GET(self):
        if self.path == "/meta":
            self.json_response(metadata)
        elif self.path.startswith("/stats/"):
            self.json_response(state.snapshot(self.path[len("/stats/"):]))
        else:
            self.artifact(head=False)

    def disconnected(self):
        readable, _, _ = select.select([self.connection], [], [], 0.03)
        return bool(readable) and self.connection.recv(1, 2) == b""

    def artifact(self, head):
        case_id = self.path[len("/artifact/"):]
        if not self.path.startswith("/artifact/") or case_id not in state.cases:
            self.send_error(404)
            return
        with state.lock:
            case = state.cases[case_id]
            data = archives[case["archive"]]
            mode = case["mode"]
            identity = data.etag_identity if isinstance(data, SparseArchive) else hashlib.sha256(data).hexdigest()
            etag = '"' + identity[:16] + '-' + str(case["revision"]) + '"'
            request = {
                "method": self.command, "range": self.headers.get("Range"),
                "if_match": self.headers.get("If-Match"), "status": None,
                "sent": 0, "expected": None, "finished": False,
            }
            get_number = sum(row["method"] == "GET" for row in case["requests"])
            case["requests"].append(request)
        try:
            if head:
                request["status"] = 200
                self.send_response(200)
                self.send_header("Content-Length", str(len(data)))
                if mode not in ("head_no_etag", "no_etag"):
                    self.send_header("ETag", etag)
                self.send_header("Connection", "close")
                self.end_headers()
                return
            match = re.fullmatch(r"bytes=(\d+)-(\d+)", self.headers.get("Range", ""))
            if not match:
                request["status"] = 416
                self.send_error(416)
                return
            start, end = map(int, match.groups())
            expected = end - start + 1
            request["expected"] = expected
            if mode == "changed_validator":
                etag = '"changed-after-head"'
            if self.headers.get("If-Match") not in (None, etag):
                request["status"] = 412
                self.send_response(412)
                self.send_header("Content-Length", "0")
                self.send_header("Connection", "close")
                self.end_headers()
                return
            directory = metadata[case["archive"]]
            interrupted_cache = mode == "interrupt_cache" and start == directory["central_offset"] + 65536
            if (mode == "transient" and get_number == 0) or interrupted_cache:
                request["status"] = 503
                self.send_response(503)
                self.send_header("Content-Length", "0")
                self.send_header("Connection", "close")
                self.end_headers()
                return
            status = 200 if mode == "ignored_range" else 206
            body = data[start:end + 1]
            chunked = mode in ("no_length", "oversized_stream")
            slow = mode in ("ignored_range", "bad_range", "bad_total", "encoded", "oversized_length", "oversized_stream")
            if mode == "ignored_range":
                body = b"X" * MAX_BODY
            elif mode == "oversized_stream":
                body += b"X" * (MAX_BODY - len(body))
            elif mode == "oversized_length":
                body += b"X"
            request["status"] = status
            self.send_response(status)
            range_start = start + 1 if mode == "bad_range" else start
            total = len(data) + 1 if mode == "bad_total" else len(data)
            self.send_header("Content-Range", f"bytes {range_start}-{end}/{total}")
            if mode != "no_etag":
                self.send_header("ETag", etag)
            self.send_header("Connection", "close")
            if mode == "encoded":
                self.send_header("Content-Encoding", "gzip")
            if chunked:
                self.send_header("Transfer-Encoding", "chunked")
            else:
                self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if mode == "truncated":
                body = body[:-1]
            if mode == "stalled":
                self.wfile.write(body[:1])
                self.wfile.flush()
                request["sent"] = 1
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    if self.disconnected():
                        return
                return
            if slow and self.disconnected():
                return
            for offset in range(0, len(body), 1024 if slow else 65536):
                part = body[offset:offset + (1024 if slow else 65536)]
                if chunked:
                    self.wfile.write(f"{len(part):X}\r\n".encode() + part + b"\r\n")
                else:
                    self.wfile.write(part)
                self.wfile.flush()
                request["sent"] += len(part)
                if slow:
                    time.sleep(0.004)
            if chunked:
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            request["finished"] = True
            self.close_connection = True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ready-file", required=True)
    options = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    server.daemon_threads = True
    Path(options.ready_file).write_text(json.dumps({"base_url": f"http://127.0.0.1:{server.server_port}"}), encoding="utf-8")
    server.serve_forever(poll_interval=0.1)


if __name__ == "__main__":
    main()
