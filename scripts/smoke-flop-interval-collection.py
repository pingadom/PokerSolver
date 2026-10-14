"""Check default absence or both physically replayed cached flop question protocols."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

BASE='http://localhost:18080/api/v1/trainer/research/flop-interval-collection'
PIN='fb77bda6bf843e8a2e4ec49c91070e608b240e50e770674601413351ad099436'
def call(path='',body=None,expected_status=200):
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    try: response=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as error: response=error
    with response:
        assert response.status==expected_status,(path,response.status,expected_status)
        result=json.load(response)
        if expected_status==200: assert 'no-store' in response.headers.get('Cache-Control','')
        return result
def wait_for_metadata(timeout=180):
    deadline=time.monotonic()+timeout
    while True:
        try: return call()
        except (urllib.error.URLError,TimeoutError,ConnectionError,AssertionError):
            if time.monotonic()>=deadline: raise
            time.sleep(2)
def run(disabled):
    if disabled:
        for path,body in [('',None),('/questions/0?collectionHash='+PIN,None),('/grade',{}),('/review',{})]: call(path,body,404)
        print('Flop collection routes are absent without opt-in'); return
    metadata=wait_for_metadata()
    assert metadata['collectionHash']==PIN and metadata['availableQuestions']==2
    assert metadata['activePlayers']==2 and metadata['maximumAttempts']==100 and metadata['trainerAdmission'] is False
    assert metadata['questionKinds']==['INITIAL_CHECK_BET','BB_FACING_BET']
    assert metadata['sourceHistoryReachStatus']=='UNAVAILABLE_NO_SOURCE_POLICY'
    expected=json.loads((Path(__file__).resolve().parents[1]/'docs/data/supplied-two-flop-interval-collection-review.json').read_bytes())
    for q in expected['questions']: assert call('/questions/'+str(q['index'])+'?collectionHash='+PIN)==q
    choices=[]
    for a in expected['attempts']:
        d=a['feedback']['decision']; f=d['initial'] or d['later']
        choice=dict(questionIndex=a['questionIndex'],reportHash=f['reportHash'],action=a['action'])
        assert call('/grade',dict(collectionHash=PIN,**choice))==a['feedback']; choices.append(choice)
    assert call('/review',dict(collectionHash=PIN,choices=choices))==expected
    assert call('/review',dict(collectionHash=PIN,choices=[]))['counts']['distinctQuestionsAttempted']==0
    repeated=call('/review',dict(collectionHash=PIN,choices=[choices[0]]*100))
    assert repeated['counts']['distinctQuestionsAttempted']==1 and repeated['counts']['withinLimit']==100
    for body in [dict(collectionHash='0'*64,**choices[0]),dict(collectionHash=PIN,**dict(choices[0],reportHash=choices[1]['reportHash'])),
                 dict(collectionHash=PIN,**dict(choices[0],action='fold')),dict(collectionHash=PIN,**choices[0],evLossBb=0)]:
        assert call('/grade',body,400)['code']=='INVALID_REQUEST'
    assert call('/review',dict(collectionHash=PIN,choices=[choices[0]]*101),400)['code']=='INVALID_REQUEST'
    print('Both pinned physical replays, typed questions, four grades, mixed review, caps and invalid requests passed')
if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--disabled',action='store_true'); parser.add_argument('--base-url',default='http://localhost:18080')
    args=parser.parse_args(); BASE=args.base_url.rstrip('/')+'/api/v1/trainer/research/flop-interval-collection'; run(args.disabled)
