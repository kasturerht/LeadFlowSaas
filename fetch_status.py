import urllib.request
import json

url = 'https://firestore.googleapis.com/v1/projects/nexaleads-98a12/databases/(default)/documents/leads?pageSize=300'
req = urllib.request.Request(url)
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        statuses = set()
        for doc in data.get('documents', []):
            fields = doc.get('fields', {})
            status = fields.get('status', {}).get('stringValue', '')
            statuses.add(status)
        print("Unique Statuses in DB:")
        for s in statuses:
            print(f"- {s}")
except Exception as e:
    print(f"Error: {e}")
