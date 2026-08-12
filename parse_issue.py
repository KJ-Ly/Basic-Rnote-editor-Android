import re

html_path = r"C:\Users\tecki\.gemini\antigravity\brain\19f9170c-7075-444d-ba29-e8c0c641cf29\.system_generated\steps\831\content.md"
with open(html_path, "r", encoding="utf-8", errors="ignore") as f:
    text = f.read()

matches = re.findall(r'issuecomment-\d+.*?(?=\bissuecomment-\d+|\Z)', text, re.DOTALL)
for i in range(min(4, len(matches))):
    clean = re.sub(r'<[^>]+>', ' ', matches[i])
    clean = ' '.join(clean.split())
    print(f"\n--- Comment {i+1} ---")
    print(clean[:1200])
