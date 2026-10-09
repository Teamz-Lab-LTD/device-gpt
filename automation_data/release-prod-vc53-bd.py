"""vc53 -> PRODUCTION, Bangladesh only (inProgress, 99%). vc52 stays the completed release for every
other country. Refuses while any production release is IN_REVIEW. Re-reads state afterwards."""
import os, socket
socket.setdefaulttimeout(600)
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
P='com.teamz.lab.debugger'; AAB='app/build/outputs/bundle/release/app-release.aab'
en=open('fastlane/metadata/android/en-US/changelogs/53.txt').read().strip(); bn=open('fastlane/metadata/android/bn-BD/changelogs/53.txt').read().strip()
assert len(en)<=500 and len(bn)<=500
c=service_account.Credentials.from_service_account_file(os.path.expanduser('~/.config/teamzlab/play-console-service-account.json'),scopes=['https://www.googleapis.com/auth/androidpublisher'])
s=build('androidpublisher','v3',credentials=c,cache_discovery=False)
def states(): return {r['releaseName']:r['releaseLifecycleState'] for r in s.applications().tracks().releases().list(parent=f'applications/{P}/tracks/production').execute()['releases']}
st=states(); print('production now:',st)
if any(v.endswith('IN_REVIEW') for v in st.values()): raise SystemExit('REFUSED: a production release is IN_REVIEW. Nothing uploaded.')
e=s.edits().insert(packageName=P,body={}).execute(); eid=e['id']
try:
    cur=s.edits().tracks().get(packageName=P,editId=eid,track='production').execute()
    live=[r for r in cur.get('releases',[]) if r.get('status')=='completed']; assert live and live[0]['versionCodes']==['52'], cur
    b=s.edits().bundles().upload(packageName=P,editId=eid,media_body=MediaFileUpload(AAB,mimetype='application/octet-stream',resumable=True,chunksize=5*1024*1024)).execute()
    assert str(b['versionCode'])=='53', b; print('bundle uploaded: versionCode',b['versionCode'])
    s.edits().tracks().update(packageName=P,editId=eid,track='production',body={'track':'production','releases':[
        {'name':'3.1.34 (53)','versionCodes':['53'],'status':'inProgress','userFraction':0.99,
         'countryTargeting':{'countries':['BD'],'includeRestOfWorld':False},
         'releaseNotes':[{'language':'en-US','text':en},{'language':'bn-BD','text':bn}]},
        live[0]]}).execute()
    s.edits().validate(packageName=P,editId=eid).execute(); print('validated')
    s.edits().commit(packageName=P,editId=eid).execute(); print('COMMITTED edit',eid); eid=None
finally:
    if eid: s.edits().delete(packageName=P,editId=eid).execute(); print('edit discarded — nothing published')
print('production after:',states())
e=s.edits().insert(packageName=P,body={}).execute()['id']
for r in s.edits().tracks().get(packageName=P,editId=e,track='production').execute().get('releases',[]):
    print('  ',r.get('name'),r.get('status'),r.get('versionCodes'),'fraction',r.get('userFraction'),'countries',r.get('countryTargeting'))
s.edits().delete(packageName=P,editId=e).execute()
