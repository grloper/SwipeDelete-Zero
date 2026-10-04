#!/usr/bin/env python3
"""Non-destructive smoke on a disposable CI emulator, using the canonical APK."""
import hashlib,json,pathlib,re,subprocess,time,xml.etree.ElementTree as ET,zlib,struct
OUT=pathlib.Path('ci-evidence'); SC=OUT/'screens';SC.mkdir(parents=True,exist_ok=True)
PKG='com.swipedelete.zero.debug';APK=OUT/'apk/app-play-debug.apk';steps=[];scroll_trace=[];queue_openings=[]
def adb(*args): return subprocess.check_output(['adb',*args],stderr=subprocess.STDOUT)
def capture(name):
 adb('shell','uiautomator','dump','/sdcard/swiperise-window.xml')
 raw=adb('shell','cat','/sdcard/swiperise-window.xml');(SC/(name+'.xml')).write_bytes(raw)
 (SC/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 return ET.fromstring(raw)
def nodes(): return list(capture('latest').iter('node'))

def queue_scroll_surface(tree):
 assert any(n.get('content-desc')=='Drag handle' for n in tree.iter('node')), 'Queue dialog disappeared during safety inspection'
 surfaces=[n for n in tree.iter('node') if n.get('scrollable')=='true']
 assert len(surfaces)==1, 'Expected one scroll surface inside the queue dialog'
 return tuple(map(int,re.findall(r'\d+',surfaces[0].get('bounds'))))

def wait_for_populated_queue(timeout=15):
 deadline=time.time()+timeout;previous=None;dialog_seen=False
 _,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size').decode())[-1])
 observations=[];queue_openings.append(observations)
 while time.time()<deadline:
  tree=capture(f'queue-opening-{len(queue_openings)}-{len(observations)}')
  if not any(n.get('content-desc')=='Drag handle' for n in tree.iter('node')):
   assert not dialog_seen, 'Queue dialog disappeared while opening'
   time.sleep(.3)
   continue
  dialog_seen=True
  ready=any(n.get('text')=='Unstage all' for n in tree.iter('node'))
  if not ready or not any(n.get('scrollable')=='true' for n in tree.iter('node')):
   observations.append({'bounds':None,'populated':ready})
   (OUT/'queue-viewport.json').write_text(json.dumps(queue_openings,indent=2)+'\n')
   previous=None
   time.sleep(.3)
   continue
  bounds=queue_scroll_surface(tree)
  observations.append({'bounds':bounds,'populated':ready})
  (OUT/'queue-viewport.json').write_text(json.dumps(queue_openings,indent=2)+'\n')
  if ready and bounds==previous and bounds[3]-bounds[1] >= height*.7:
   return
  previous=bounds
  time.sleep(.3)
 raise AssertionError('Populated queue did not settle with a usable expanded viewport')

def scroll_to(pattern, direction='down', max_scrolls=8):
 """Reach an actual lazy-list control without assuming it is initially composed."""
 for attempt in range(max_scrolls+1):
  capture_name=f'queue-scroll-{len(scroll_trace):02d}'
  tree=capture(capture_name)
  x1,y1,x2,y2=queue_scroll_surface(tree)
  observation={'capture':capture_name,'target':pattern,'attempt':attempt,'direction':direction,'bounds':[x1,y1,x2,y2]}
  scroll_trace.append(observation)
  (OUT/'queue-scroll-trace.json').write_text(json.dumps(scroll_trace,indent=2)+'\n')
  if any(re.search(pattern,(n.get('text','')+' '+n.get('content-desc','')).strip()) for n in tree.iter('node')):
   return tree
  if attempt == max_scrolls: break
  top=y1+(y2-y1)//4;bottom=y2-(y2-y1)//4
  start,end=(bottom,top) if direction=='down' else (top,bottom)
  observation['gesture']=[(x1+x2)//2,start,(x1+x2)//2,end]
  (OUT/'queue-scroll-trace.json').write_text(json.dumps(scroll_trace,indent=2)+'\n')
  adb('shell','input','swipe',str((x1+x2)//2),str(start),str((x1+x2)//2),str(end),'450')
  time.sleep(1)
 raise AssertionError('Missing scroll-reachable control: '+pattern)
def click(pattern, timeout=20, scroll=False):
 deadline=time.time()+timeout
 scrolls=0
 while time.time()<deadline:
  tree=capture('latest')
  parents={child:parent for parent in tree.iter() for child in parent}
  for n in tree.iter('node'):
   ancestors=[n]
   while ancestors[-1] in parents: ancestors.append(parents[ancestors[-1]])
   if re.search(pattern,(n.get('text','')+' '+n.get('content-desc','')).strip()) and n.get('enabled')=='true' and not any(a.get('enabled')=='false' for a in ancestors):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1);return
  if scroll and scrolls < 3:
   width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size').decode())[-1])
   adb('shell','input','swipe',str(width//2),str(int(height*.78)),str(width//2),str(int(height*.32)),'450')
   scrolls+=1
  time.sleep(1)
 raise AssertionError('Missing enabled control: '+pattern)
def start(): adb('shell','am','start','-W','-n',PKG+'/com.swipedelete.zero.MainActivity');time.sleep(2)
def png(color):
 def chunk(t,b): return struct.pack('!I',len(b))+t+b+struct.pack('!I',zlib.crc32(t+b)&0xffffffff)
 data=b''.join(b'\0'+bytes(color)*512 for _ in range(512))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',512,512,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(data))+chunk(b'IEND',b'')
try:
 assert adb('shell','getprop','ro.kernel.qemu').strip()==b'1','Disposable emulator required'
 adb('install','-r',str(APK));steps.append('install canonical APK')
 for perm in ['READ_MEDIA_IMAGES','READ_MEDIA_VIDEO']:
  adb('shell','pm','grant',PKG,'android.permission.'+perm)
 test_apk=OUT/'apk/app-play-debug-androidTest.apk'
 adb('install','-r',str(test_apk))
 result=adb('shell','am','instrument','-w','-e','class','com.swipedelete.zero.MediaFixtureInstrumentedTest',PKG+'.test/androidx.test.runner.AndroidJUnitRunner').decode()
 (OUT/'media-fixtures.txt').write_text(result)
 assert 'OK (1 test)' in result,result
 media=adb('shell','content','query','--uri','content://media/external/images/media','--projection','_id:_display_name:_size:is_pending').decode()
 (OUT/'media-fixtures.txt').write_text(result+'\n'+media)
 for i in range(3):
  assert re.search(rf'Screenshot_fixture_{i}\.png.*_size=(?!NULL|0\b)\d+',media), 'Committed synthetic image missing from MediaStore'
 steps.append('three positive-size synthetic images committed via MediaStore')
 start();capture('01-dashboard');steps.append('permissions and dashboard')
 click(r'Start review|Browse library', scroll=True)
 if any('Got it' in n.get('text','') for n in nodes()):click(r'Got it')
 click(r'^Keep\b');click(r'^Undo\b');steps.append('keep and undo')
 click(r'^Stage\b');click(r'^Undo\b');click(r'^Stage\b');steps.append('stage, undo, stage again')
 capture('02-review');click(r'^Back\b')
 click(r'Review [1-9].*staged files');wait_for_populated_queue();capture('03-staging')
 scroll_to(r'Cleanup is unavailable')
 assert any('Cleanup is unavailable' in n.get('text','') for n in nodes()), 'Safety lock explanation missing'
 # Both selection modes may change, but neither execution control can be enabled.
 for mode in ['Permanent Delete','30-Day OS Trash']:
  scroll_to(mode, direction='up')
  click(mode)
  scroll_to(r'Move to Android Trash|Delete and Free Up')
  tree=capture('04-lock-'+('permanent' if mode.startswith('Permanent') else 'trash'))
  parents={child:parent for parent in tree.iter() for child in parent}
  controls=[n for n in tree.iter('node') if ('Move to Android Trash' in n.get('text','') or 'Delete and Free Up' in n.get('text',''))]
  assert controls, 'Cleanup control not found'
  for control in controls:
   ancestry=[control]
   while ancestry[-1] in parents: ancestry.append(parents[ancestry[-1]])
   assert any(n.get('enabled')=='false' for n in ancestry), 'Cleanup control enabled'
 steps.append('locked cleanup explanation and modes')
 adb('shell','am','force-stop',PKG);start();click(r'Review [1-9].*staged files');wait_for_populated_queue();capture('05-persisted')
 click(r'Unstage all');steps.append('queue survives restart; unstage works')
 capture('06-restored')
 for i in range(3):
  assert re.search(rf'Screenshot_fixture_{i}\.png.*_size=(?!NULL|0\b)\d+',adb('shell','content','query','--uri','content://media/external/images/media','--projection','_display_name:_size').decode())
 steps.append('all synthetic originals remain')
 status='PASS'
except Exception as e:
 status='FAILED';steps.append(str(e));(OUT/'failure-logcat.txt').write_bytes(adb('logcat','-d','-t','1200'));raise
finally:
 (OUT/'smoke.json').write_text(json.dumps({'status':status,'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest(),'steps':steps,'scope':'isolated emulator, synthetic local files; no cloud or deletion'},indent=2)+'\n')
