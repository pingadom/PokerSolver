"""Exercise default absence or the full eleven-question BB/BTN production startup and cache."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

PREFIX='/api/v1/trainer/research/flop-interval-collection-v4'
BASE='http://localhost:18080'+PREFIX
PIN='57abbb5f5bbc10b2227739299c1eec0f0092a7a136dc80e63c0830865cb7fd87'
STARTUP_SECONDS=1200


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
        print('Eleven-question mixed flop collection is absent without opt-in');return
    metadata=wait_for_metadata()
    assert metadata['collectionHash']==PIN and metadata['availableQuestions']==11 and metadata['activePlayers']==2
    assert metadata['maximumAttempts']==100 and metadata['trainerAdmission'] is False
    assert metadata['questionKinds']==['LEGACY_TEN_QUESTION_COLLECTION']*10+['QUALIFIED_WEAK_HAND_FLOP']
    assert metadata['beliefs']=='INDEPENDENT_SOURCE_SPECIFIC_SUPPLIED_JOINT_PRIORS/v1'
    assert metadata['legacyCollectionHash']=='11db67eb188fe4fadf8b370f45ecfc0f29ed5412ae78d69eff514dcecc962122'
    assert metadata['sourceHistoryReachStatus']=='UNAVAILABLE_NO_SOURCE_POLICY'
    docs_url=BASE.removesuffix(PREFIX)+'/v3/api-docs'
    with urllib.request.urlopen(docs_url,timeout=20) as response:docs=json.load(response)
    schemas=docs['components']['schemas']
    for route,name,fields in [('grade','QualifiedFlopV4GradeRequest',{'collectionHash','questionIndex','reportHash','action'}),('review','QualifiedFlopV4ReviewRequest',{'collectionHash','choices'}),('review/export','QualifiedFlopV4ReviewRequest',{'collectionHash','choices'})]:
        request=docs['paths'][PREFIX+'/'+route]['post']['requestBody'];assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref']=='#/components/schemas/'+name
        assert set(schemas[name]['properties'])==fields
    assert set(schemas['QualifiedFlopV4SubmittedChoice']['properties'])=={'questionIndex','reportHash','action'}
    assert schemas['QualifiedFlopV4ReviewRequest']['properties']['choices']['items']['$ref']=='#/components/schemas/QualifiedFlopV4SubmittedChoice'
    path=Path(__file__).resolve().parents[1]/'docs/data/supplied-eleven-question-flop-collection-review.json'
    expected_bytes=path.read_bytes();expected=json.loads(expected_bytes)
    choices=[]
    for q in expected['questions']:
        assert call('/questions/'+str(q['index'])+'?collectionHash='+PIN)==q
        assert q['sourceQuestionIndex']==(q['index'] if q['index']<10 else 0)
    for a in expected['attempts']:
        union=a['feedback']['decision'];original=union['weak'] if union['weak'] else next(union['legacy']['decision'][key] for key in ['initial','later','btn','paired'] if union['legacy']['decision'][key] is not None)
        choice=dict(questionIndex=a['questionIndex'],reportHash=original['reportHash'],action=a['action'])
        assert call('/grade',dict(collectionHash=PIN,**choice))==a['feedback'];choices.append(choice)
    assert call('/review',dict(collectionHash=PIN,choices=choices))==expected
    assert call('/review',dict(collectionHash=PIN,choices=[]))['counts']['distinctQuestionsAttempted']==0
    assert call('/review',dict(collectionHash=PIN,choices=[choices[-1]]*100))['counts']==dict(distinctQuestionsAttempted=1,withinLimit=0,outsideLimit=100,strategyDependent=0)
    request=urllib.request.Request(BASE+'/review/export',data=json.dumps(dict(collectionHash=PIN,choices=choices)).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=20) as response:
        assert response.read()==expected_bytes
        assert response.headers.get_content_type()=='application/json' and 'no-store' in response.headers.get('Cache-Control','')
        assert 'pokerlab-qualified-flop-review-'+PIN[:12]+'.json' in response.headers.get('Content-Disposition','')
    for body in [dict(collectionHash='0'*64,**choices[0]),dict(collectionHash=PIN,**dict(choices[0],reportHash='0'*64)),dict(collectionHash=PIN,**dict(choices[0],questionIndex=11)),dict(collectionHash=PIN,**dict(choices[-1],action='check')),dict(collectionHash=PIN,**choices[0],sourceQuestionIndex=0)]:
        assert call('/grade',body,400)['code']=='INVALID_REQUEST'
    for route in ['/review','/review/export']:
        for body in [{},dict(collectionHash='0'*64,choices=[]),dict(collectionHash=PIN,choices=[choices[0]]*101),dict(collectionHash=PIN,choices=[dict(choices[0],evLossBb=0)])]:
            assert call(route,body,400)['code']=='INVALID_REQUEST'
    good=json.dumps(dict(collectionHash=PIN,**choices[-1]))
    for raw in [good+' {}',good[:-1]+',"action":"fold"}',good.replace('"questionIndex": 10','"questionIndex": "10"'),good[:-1]+',"evLossBb":0}', 'null', ' '*32769]:
        request=urllib.request.Request(BASE+'/grade',data=raw.encode(),headers={'Content-Type':'application/json'})
        try:response=urllib.request.urlopen(request,timeout=20)
        except urllib.error.HTTPError as error:response=error
        with response:assert response.status==400 and json.load(response)['code']=='INVALID_REQUEST'
    print('Five complete source replays, eleven typed questions, twenty-two grades, exact review/export, strict bytes and OpenAPI passed')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--disabled',action='store_true');parser.add_argument('--base-url',default='http://localhost:18080');args=parser.parse_args()
    BASE=args.base_url.rstrip('/')+PREFIX;run(args.disabled)
