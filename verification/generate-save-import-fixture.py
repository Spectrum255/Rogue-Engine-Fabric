from __future__ import annotations

import argparse
import gzip
import json
import os
import struct
import zipfile
import zlib
from pathlib import Path


TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12
SECTOR_SIZE = 4096


def text(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + text(name) + payload


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


def string(value: str) -> bytes:
    return text(value)


def tag_list(element_type: int, values: list[bytes]) -> bytes:
    return bytes([element_type]) + struct.pack(">i", len(values)) + b"".join(values)


def int_list(values: list[int]) -> bytes:
    return tag_list(TAG_INT, [integer(value) for value in values])


def int_array(values: list[int]) -> bytes:
    return struct.pack(">i", len(values)) + b"".join(integer(value) for value in values)


def long_array(values: list[int]) -> bytes:
    return struct.pack(">i", len(values)) + b"".join(struct.pack(">q", value) for value in values)


def float_list(values: list[float]) -> bytes:
    return tag_list(TAG_FLOAT, [float32(value) for value in values])


def double_list(values: list[float]) -> bytes:
    return tag_list(TAG_DOUBLE, [double(value) for value in values])


def compound_list(values: list[bytes]) -> bytes:
    return tag_list(TAG_COMPOUND, values)


def compound(fields: list[tuple[str, int, bytes]]) -> bytes:
    return b"".join(named(tag_type, name, payload) for name, tag_type, payload in fields) + b"\x00"


def root_compound(fields: list[tuple[str, int, bytes]]) -> bytes:
    return b"\x0a\x00\x00" + compound(fields)


def pack_block_indices(indices: list[int], bits: int = 4) -> list[int]:
    values_per_long = 64 // bits
    packed = []
    for start in range(0, len(indices), values_per_long):
        value = 0
        for offset, index in enumerate(indices[start:start + values_per_long]):
            value |= index << (bits * offset)
        packed.append(value)
    return packed


def write_region(path: Path, chunks: dict[tuple[int, int], bytes]) -> None:
    locations = bytearray(SECTOR_SIZE * 2)
    payloads = bytearray()
    next_sector = 2
    for (chunk_x, chunk_z), chunk_nbt in sorted(chunks.items()):
        compressed = zlib.compress(chunk_nbt, level=9)
        chunk_payload = struct.pack(">I", len(compressed) + 1) + b"\x02" + compressed
        sectors = (len(chunk_payload) + SECTOR_SIZE - 1) // SECTOR_SIZE
        if sectors > 255:
            raise ValueError("Fixture chunk is too large for one Anvil location entry")
        location_index = ((chunk_x % 32) + (chunk_z % 32) * 32) * 4
        locations[location_index:location_index + 4] = (
            next_sector.to_bytes(3, "big") + bytes([sectors])
        )
        payloads.extend(chunk_payload)
        payloads.extend(b"\x00" * (sectors * SECTOR_SIZE - len(chunk_payload)))
        next_sector += sectors
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(locations + payloads)


def world_version(repo: Path) -> int:
    candidates = [
        os.environ.get("ROGUE_SERVER_JAR"),
        repo / "server.jar",
        repo / "versions/26.3/server-26.3.jar",
    ]
    server = next((Path(value) for value in candidates if value and Path(value).is_file()), None)
    if server is None:
        raise FileNotFoundError(
            "Minecraft 26.3 server JAR not found. Place it at server.jar, "
            "versions/26.3/server-26.3.jar, or set ROGUE_SERVER_JAR."
        )
    with zipfile.ZipFile(server) as archive:
        version = json.loads(archive.read("version.json"))
    value = version["world_version"]
    if not isinstance(value, int):
        raise ValueError("version.json world_version must be an integer")
    return value


def generate(repo: Path, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    version = world_version(repo)
    level = root_compound([
        ("Data", TAG_COMPOUND, compound([("DataVersion", TAG_INT, integer(version))])),
    ])
    (output / "level.dat").write_bytes(gzip.compress(level, mtime=0))

    stone = compound([("id", TAG_STRING, string("minecraft:stone"))])
    chest_state = compound([("id", TAG_STRING, string("minecraft:chest"))])
    indices = [0] * 4096
    indices[(2 << 4) | 2] = 1
    block_states = compound([
        ("palette", TAG_LIST, compound_list([stone, chest_state])),
        ("data", TAG_LONG_ARRAY, long_array(pack_block_indices(indices))),
    ])
    section = compound([
        ("Y", TAG_BYTE, byte(8)),
        ("block_states", TAG_COMPOUND, block_states),
    ])
    chest = compound([
        ("id", TAG_STRING, string("minecraft:chest")),
        ("x", TAG_INT, integer(2)),
        ("y", TAG_INT, integer(128)),
        ("z", TAG_INT, integer(2)),
        ("Items", TAG_LIST, compound_list([])),
    ])
    chunk = root_compound([
        ("xPos", TAG_INT, integer(0)),
        ("zPos", TAG_INT, integer(0)),
        ("Status", TAG_STRING, string("minecraft:full")),
        ("sections", TAG_LIST, compound_list([section])),
        ("block_entities", TAG_LIST, compound_list([chest])),
    ])
    write_region(output / "region/r.0.0.mca", {(0, 0): chunk})

    item = compound([
        ("id", TAG_STRING, string("minecraft:item")),
        ("Pos", TAG_LIST, double_list([2.5, 129.0, 2.5])),
        ("Rotation", TAG_LIST, float_list([0.0, 0.0])),
        ("Motion", TAG_LIST, double_list([0.0, 0.0, 0.0])),
        ("OnGround", TAG_BYTE, byte(1)),
        ("Age", TAG_SHORT, short(0)),
        ("PickupDelay", TAG_SHORT, short(0)),
        ("Item", TAG_COMPOUND, compound([
            ("id", TAG_STRING, string("minecraft:stone")),
            ("count", TAG_BYTE, byte(1)),
        ])),
    ])
    entity_chunk = root_compound([
        ("Entities", TAG_LIST, compound_list([item])),
        ("Position", TAG_INT_ARRAY, int_array([0, 0])),
    ])
    write_region(output / "entities/r.0.0.mca", {(0, 0): entity_chunk})

    archive = output.parent / "save-import.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for relative in ("level.dat", "region/r.0.0.mca", "entities/r.0.0.mca"):
            target.write(output / relative, relative)
    print(f"Generated {output} (DataVersion={version})")
    print(f"Generated {archive}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a minimal Minecraft 26.3 Anvil import fixture")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    generate(repo, args.output.resolve())


if __name__ == "__main__":
    main()
