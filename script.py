import json

with open(r'C:\Users\Admin\.gemini\antigravity\brain\3183cee6-4b5c-40f2-80c0-fd2fb5fda775\.system_generated\logs\transcript.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        obj = json.loads(line)
        if 'tool_calls' in obj and obj['tool_calls']:
            tool = obj['tool_calls'][0]
            if tool['name'] == 'write_to_file':
                target = tool['args'].get('TargetFile', '')
                if target.endswith('DashboardScreen.kt"'):
                    content = tool['args']['CodeContent']
                    content = content.encode('utf-8').decode('unicode_escape')
                    with open('dash_real_v1.kt', 'w', encoding='utf-8') as out:
                        out.write(content)
                    break
