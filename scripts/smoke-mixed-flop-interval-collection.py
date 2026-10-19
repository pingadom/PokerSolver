"""Exercise default absence or the full six-question BB/BTN production startup and cache."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

PREFIX='/api/v1/trainer/research/flop-interval-collection-v2'
BASE='http://localhost:18080'+PREFIX
PIN='1694d51922de3619a3523b9ed07dbe6375a742c882632e18eafc3f7fe7c6cb2b'
STARTUP_SECONDS=900


def call(path='',body=None,expected_status=200):
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    try:response=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as error:response=error
    with response:
        assert response.status==expected_status,(path,response.status,expected_status)
        result=json.load(response)
        if expected_status==200:assert 'no-store' in response.headers.get('Cache-Control','')
        return result


def wait_for_metadata(timeout=STARTUP_SECONDS):
    deadline=time.monotonic()+timeout
    while True:
        try:return call()
        except (urllib.error.URLError,TimeoutError,ConnectionError,AssertionError):
            if time.monotonic()>=deadline:raise
            time.sleep(2)


def run(disabled):
    if disabled:
        for path,body in [('',None),('/questions/0?collectionHash='+PIN,None),('/grade',{}),('/review',{}),('/review/export',{})]:call(path,body,404)
        print('Six-question mixed flop collection is absent without opt-in');return
    metadata=wait_for_metadata()
    assert metadata['collectionHash']==PIN and metadata['availableQuestions']==6 and metadata['activePlayers']==2
    assert metadata['maximumAttempts']==100 and metadata['trainerAdmission'] is False
    assert metadata['questionKinds']==['INITIAL_CHECK_BET','BB_FACING_BET']+['BTN_AFTER_CHECK']*4
    assert metadata['sourceHistoryReachStatus']=='UNAVAILABLE_NO_SOURCE_POLICY'
    docs_url=BASE.removesuffix(PREFIX)+'/v3/api-docs'
    with urllib.request.urlopen(docs_url,timeout=20) as response:docs=json.load(response)
    schemas=docs['components']['schemas']
    for route,name,fields in [('grade','MixedFlopV2GradeRequest',{'collectionHash','questionIndex','reportHash','action'}),('review','MixedFlopV2ReviewRequest',{'collectionHash','choices'}),('review/export','MixedFlopV2ReviewRequest',{'collectionHash','choices'})]:
        request=docs['paths'][PREFIX+'/'+route]['post']['requestBody'];assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref']=='#/components/schemas/'+name
        assert set(schemas[name]['properties'])==fields
    assert set(schemas['MixedFlopV2SubmittedChoice']['properties'])=={'questionIndex','reportHash','action'}
    assert schemas['MixedFlopV2ReviewRequest']['properties']['choices']['items']['$ref']=='#/components/schemas/MixedFlopV2SubmittedChoice'
    path=Path(__file__).resolve().parents[1]/'docs/data/supplied-six-question-flop-collection-review.json'
    expected_bytes=path.read_bytes();expected=json.loads(expected_bytes)
    choices=[]
    for q in expected['questions']:
        assert call('/questions/'+str(q['index'])+'?collectionHash='+PIN)==q
        assert q['sourceQuestionIndex']==(0 if q['index']<2 else q['index']-2)
    for a in expected['attempts']:
        union=a['feedback']['decision'];original=next(union[key] for key in ['initial','later','btn'] if union[key] is not None)
        choice=dict(questionIndex=a['questionIndex'],reportHash=original['reportHash'],action=a['action'])
        assert call('/grade',dict(collectionHash=PIN,**choice))==a['feedback'];choices.append(choice)
    assert call('/review',dict(collectionHash=PIN,choices=choices))==expected
    assert call('/review',dict(collectionHash=PIN,choices=[]))['counts']['distinctQuestionsAttempted']==0
    assert call('/review',dict(collectionHash=PIN,choices=[choices[-1]]*100))['counts']==dict(distinctQuestionsAttempted=1,withinLimit=100,outsideLimit=0,strategyDependent=0)
    request=urllib.request.Request(BASE+'/review/export',data=json.dumps(dict(collectionHash=PIN,choices=choices)).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=20) as response:
        assert response.read()==expected_bytes
        assert response.headers.get_content_type()=='application/json' and 'no-store' in response.headers.get('Cache-Control','')
        assert 'pokerlab-mixed-flop-review-'+PIN[:12]+'.json' in response.headers.get('Content-Disposition','')
    for body in [dict(collectionHash='0'*64,**choices[0]),dict(collectionHash=PIN,**dict(choices[0],reportHash='0'*64)),dict(collectionHash=PIN,**dict(choices[0],questionIndex=6)),dict(collectionHash=PIN,**dict(choices[1],action='bet')),dict(collectionHash=PIN,**choices[0],sourceQuestionIndex=0)]:
        assert call('/grade',body,400)['code']=='INVALID_REQUEST'
    for route in ['/review','/review/export']:
        for body in [{},dict(collectionHash='0'*64,choices=[]),dict(collectionHash=PIN,choices=[choices[0]]*101),dict(collectionHash=PIN,choices=[dict(choices[0],evLossBb=0)])]:
            assert call(route,body,400)['code']=='INVALID_REQUEST'
    print('Complete three-source replay, six typed questions, twelve grades, both index spaces, exact canonical export, OpenAPI, caps and strict rejections passed')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--disabled',action='store_true');parser.add_argument('--base-url',default='http://localhost:18080');args=parser.parse_args()
    BASE=args.base_url.rstrip('/')+PREFIX;run(args.disabled)
