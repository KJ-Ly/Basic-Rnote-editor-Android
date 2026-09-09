"""Compare two .rnote files and show what changed between them.

A .rnote is gzipped JSON, so the files can't be compared as-is. This decompresses
both, normalises the JSON (sorted keys, rounded floats), and prints a unified diff.

Usage:
    python diff_rnote.py before.rnote after.rnote      # what changed
    python diff_rnote.py before.rnote after.rnote -c   # just document.config
    python diff_rnote.py before.rnote after.rnote -e   # exact floats, no rounding
    python diff_rnote.py file.rnote                    # dump one file as JSON

Round-trip a file through BRNA, then diff it against the original: anything the
save path dropped or altered shows up as a - / + pair.
"""

import argparse
import difflib
import gzip
import json
import sys

# Canvas coordinates run to five figures, so four decimals is well under a pixel.
# Rounding keeps float formatting differences from burying the real changes.
DEFAULT_PLACES = 4


def load(path):
    try:
        with gzip.open(path, "rb") as f:
            return json.loads(f.read())
    except OSError as e:
        sys.exit("%s is not a gzip file (a .rnote should be): %s" % (path, e))
    except ValueError as e:
        sys.exit("%s decompressed but isn't valid JSON: %s" % (path, e))


def round_floats(node, places):
    """Walk the tree rounding every float, so 793.7 and 793.70000001 compare equal."""
    if isinstance(node, float):
        return round(node, places)
    if isinstance(node, dict):
        return {k: round_floats(v, places) for k, v in node.items()}
    if isinstance(node, list):
        return [round_floats(v, places) for v in node]
    return node


def normalise(doc, places):
    if places is not None:
        doc = round_floats(doc, places)
    return json.dumps(doc, indent=1, sort_keys=True).splitlines()


def snapshot(doc):
    return doc.get("data", {}).get("engine_snapshot", {})


def config_of(doc):
    return snapshot(doc).get("document", {})


def describe(path, doc):
    """One line of context, so a diff of nothing still tells you something."""
    es = snapshot(doc)
    strokes = [c for c in es.get("stroke_components", []) if c.get("value") is not None]
    kinds = {}
    for c in strokes:
        for k in c["value"]:
            kinds[k] = kinds.get(k, 0) + 1
    parts = ", ".join("%d %s" % (n, k) for k, n in sorted(kinds.items())) or "no elements"
    doc_cfg = config_of(doc).get("config", {})
    layout = doc_cfg.get("layout", "(no layout key)")
    return "%s\n    %s | layout: %s | version: %s" % (
        path, parts, layout, doc.get("version", "?")
    )


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("before")
    ap.add_argument("after", nargs="?", help="omit to just dump `before` as JSON")
    ap.add_argument("-c", "--config", action="store_true",
                    help="compare only the document block (format, background, layout, extent)")
    ap.add_argument("-e", "--exact", action="store_true",
                    help="compare floats exactly instead of rounding to %d places" % DEFAULT_PLACES)
    args = ap.parse_args()

    places = None if args.exact else DEFAULT_PLACES

    before = load(args.before)
    if args.after is None:
        print("\n".join(normalise(before, places)))
        return 0

    after = load(args.after)

    print(describe(args.before, before))
    print(describe(args.after, after))
    print()

    a, b = (config_of(before), config_of(after)) if args.config else (before, after)

    diff = list(difflib.unified_diff(
        normalise(a, places), normalise(b, places),
        fromfile=args.before, tofile=args.after, lineterm="", n=2,
    ))

    if not diff:
        scope = "document blocks are" if args.config else "files are"
        print("No differences: the %s identical%s." % (
            scope, "" if args.exact else " to %d decimal places" % DEFAULT_PLACES))
        return 0

    print("\n".join(diff))
    removed = sum(1 for line in diff if line.startswith("-") and not line.startswith("---"))
    added = sum(1 for line in diff if line.startswith("+") and not line.startswith("+++"))
    print("\n%d lines removed, %d added. Lines starting with - are only in %s." % (
        removed, added, args.before))
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:
        # Piping into `head` or `more` closes the pipe early; that is not an error.
        try:
            sys.stdout.close()
        except Exception:
            pass
        sys.exit(0)
