from __future__ import annotations

import gzip
import struct
from pathlib import Path


def text(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def integer(value: int) -> bytes:
    return struct.pack(">i", value)


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + text(name) + payload


def int_list(values: list[int]) -> bytes:
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(integer(value) for value in values)


def main() -> None:
    palette = named(8, "Name", text("minecraft:stone")) + b"\x00"
    blocks = []
    for z in range(5):
        for x in range(5):
            block = named(9, "pos", int_list([x, 0, z]))
            block += named(3, "state", integer(0))
            blocks.append(block + b"\x00")

    payload = named(9, "size", int_list([5, 1, 5]))
    payload += named(9, "palette", bytes([10]) + struct.pack(">i", 1) + palette)
    payload += named(9, "blocks", bytes([10]) + struct.pack(">i", len(blocks)) + b"".join(blocks))
    payload += named(9, "entities", bytes([10]) + struct.pack(">i", 0))
    root = b"\x0a\x00\x00" + payload + b"\x00"

    repo = Path(__file__).resolve().parents[1]
    target = repo / "src/main/resources/data/rogueengine/structure/example_room.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(root, mtime=0))
    print(f"Generated {target}")


if __name__ == "__main__":
    main()
