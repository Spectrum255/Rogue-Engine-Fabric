from __future__ import annotations

import gzip
import struct
from pathlib import Path


def text(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + text(name) + payload


def string(value: str) -> bytes:
    return text(value)


def byte(value: int) -> bytes:
    return struct.pack(">b", value)


def short(value: int) -> bytes:
    return struct.pack(">h", value)


def integer(value: int) -> bytes:
    return struct.pack(">i", value)


def float32(value: float) -> bytes:
    return struct.pack(">f", value)


def double(value: float) -> bytes:
    return struct.pack(">d", value)


def int_list(values: list[int]) -> bytes:
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(integer(value) for value in values)


def float_list(values: list[float]) -> bytes:
    return bytes([5]) + struct.pack(">i", len(values)) + b"".join(float32(value) for value in values)


def double_list(values: list[float]) -> bytes:
    return bytes([6]) + struct.pack(">i", len(values)) + b"".join(double(value) for value in values)


def compound(fields: list[tuple[str, int, bytes]]) -> bytes:
    return b"".join(named(tag_type, name, payload) for name, tag_type, payload in fields) + b"\x00"


def compound_list(values: list[bytes]) -> bytes:
    return bytes([10]) + struct.pack(">i", len(values)) + b"".join(values)


def main() -> None:
    stone = compound([("id", 8, string("minecraft:stone"))])
    chest_state = compound([
        ("id", 8, string("minecraft:chest")),
        ("Properties", 10, compound([
            ("facing", 8, string("north")),
            ("type", 8, string("single")),
            ("waterlogged", 8, string("false")),
        ])),
    ])
    blocks = []
    for z in range(5):
        for x in range(5):
            state = 1 if (x, z) == (2, 2) else 0
            blocks.append(compound([
                ("pos", 9, int_list([x, 0, z])),
                ("state", 3, integer(state)),
            ]))
    chest = compound([
        ("id", 8, string("minecraft:chest")),
        ("x", 3, integer(2)),
        ("y", 3, integer(0)),
        ("z", 3, integer(2)),
        ("Items", 9, compound_list([])),
    ])
    item = compound([
        ("id", 8, string("minecraft:item")),
        ("Pos", 9, double_list([2.5, 1.0, 2.5])),
        ("Rotation", 9, float_list([0.0, 0.0])),
        ("Motion", 9, double_list([0.0, 0.0, 0.0])),
        ("OnGround", 1, byte(1)),
        ("Age", 2, short(0)),
        ("PickupDelay", 2, short(0)),
        ("Item", 10, compound([
            ("id", 8, string("minecraft:stone")),
            ("count", 1, byte(1)),
        ])),
    ])
    root = compound([
        ("schema_version", 3, integer(1)),
        ("size", 9, int_list([5, 1, 5])),
        ("palette", 9, compound_list([stone, chest_state])),
        ("blocks", 9, compound_list(blocks)),
        ("block_entities", 9, compound_list([chest])),
        ("entities", 9, compound_list([item])),
    ])
    payload = b"\x0a\x00\x00" + root

    repo = Path(__file__).resolve().parents[1]
    target = repo / "src/main/resources/data/rogue-engine-fabric/rogue-engine-fabric/map_snapshot/example_room.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(payload, mtime=0))
    print(f"Generated {target}")


if __name__ == "__main__":
    main()
