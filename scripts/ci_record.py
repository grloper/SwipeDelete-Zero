#!/usr/bin/env python3
"""Record native process exit status and UTC timestamps without a masking pipe."""
import subprocess,sys,json,datetime,pathlib
name=sys.argv[1];command=sys.argv[2:];root=pathlib.Path('ci-evidence/logs');root.mkdir(parents=True,exist_ok=True)
start=datetime.datetime.now(datetime.timezone.utc).isoformat()
with (root/(name+'.log')).open('w') as out:
 p=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 for line in p.stdout:out.write(line);print(line,end='',flush=True)
 code=p.wait()
(root/(name+'.json')).write_text(json.dumps(dict(command=command,exit_code=code,started_utc=start,ended_utc=datetime.datetime.now(datetime.timezone.utc).isoformat()),indent=2)+'\n')
sys.exit(code)
