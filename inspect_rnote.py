import gzip, json

with gzip.open(r'test.rnote', 'rb') as f:
    data = json.loads(f.read())

es = data["data"]["engine_snapshot"]
sc = es["stroke_components"]
cc = es["chrono_components"]

print(f"stroke_components: {len(sc)} items")
for i, item in enumerate(sc):
    val = item.get("value")
    ver = item.get("version")
    if val is None:
        print(f"  [{i}] null (version={ver})")
    else:
        print(f"  [{i}] version={ver}, keys={list(val.keys())}")
        # Show the element structure
        for ek, ev in val.items():
            if isinstance(ev, dict):
                print(f"       {ek}: keys={list(ev.keys())[:12]}")
            else:
                print(f"       {ek}: {type(ev).__name__} = {repr(ev)[:200]}")

print(f"\nchrono_components: {len(cc)} items")
for i, item in enumerate(cc):
    val = item.get("value")
    ver = item.get("version")
    print(f"  [{i}] version={ver}, value={json.dumps(val)[:200]}")

# Show one full non-null stroke
for i, item in enumerate(sc):
    if item.get("value") is not None:
        print(f"\n=== Full stroke [{i}] ===")
        print(json.dumps(item, indent=2)[:3000])
        break
