"""Default absence or real seven-source startup, eighteen questions, every move and exact export."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

PREFIX='/api/v1/trainer/research/flop-interval-collection-v6'
BASE='http://localhost:18080'+PREFIX
PIN='b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2'
STARTUP_SECONDS=1200

def call(path='',body=None,expected_status=200,raw=False):
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    try:response=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as error:response=error
    with response:
        assert response.status==expected_status,(path,response.status,expected_status)
        data=response.read()
        if expected_status==200:assert 'no-store' in response.headers.get('Cache-Control','')
        return data if raw else json.loads(data)

def wait_for_metadata(timeout=STARTUP_SECONDS):
    deadline=time.monotonic()+timeout
    while True:
        try:return call()
        except (urllib.error.URLError,TimeoutError,ConnectionError,AssertionError):
            if time.monotonic()>=deadline:raise
            time.sleep(2)

def source_pin(question):
    if 'reportHash' in question:return question['reportHash']
    if 'decision' in question:return source_pin(question['decision'])
    values=[v for k,v in question.items() if k!='kind' and v is not None]
    assert len(values)==1
    return source_pin(values[0])

def run(disabled):
    if disabled:
        for path,body in [('',None),('/questions/0?collectionHash='+PIN,None),('/grade',{}),('/review',{}),('/review/export',{})]:call(path,body,404)
        print('Eighteen-question routes absent without opt-in');return
    metadata=wait_for_metadata()
    assert metadata['collectionHash']==PIN and metadata['availableQuestions']==18 and metadata['activePlayers']==2
    assert metadata['independentInputSets']==2 and metadata['trainerAdmission'] is False and metadata['maximumAttempts']==100
    assert metadata['startupBudget']==dict(sourceReplays=7,freshReferenceRuns=14,freshIterations=1212000)
    assert metadata['addedReferenceSharing']=='ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1'
    assert metadata['questionKinds']==['LEGACY_FOURTEEN_QUALIFIED_COLLECTION']*14+['SHARED_REFERENCE_UNPAIRED_QUESTIONS']*4
    docs_url=BASE.removesuffix(PREFIX)+'/v3/api-docs'
    with urllib.request.urlopen(docs_url,timeout=20) as response:docs=json.load(response)
    schemas=docs['components']['schemas']
    for route,name,fields in [('grade','UnpairedFlopV6GradeRequest',{'collectionHash','questionIndex','reportHash','action'}),('review','UnpairedFlopV6ReviewRequest',{'collectionHash','choices'}),('review/export','UnpairedFlopV6ReviewRequest',{'collectionHash','choices'})]:
        request=docs['paths'][PREFIX+'/'+route]['post']['requestBody'];assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref']=='#/components/schemas/'+name
        assert set(schemas[name]['properties'])==fields
    assert schemas['UnpairedFlopV6ReviewRequest']['properties']['choices']['items']['$ref']=='#/components/schemas/UnpairedFlopV6SubmittedChoice'
    file=Path(__file__).resolve().parents[1]/'docs/data/supplied-eighteen-question-flop-collection-review.json'
    expected_bytes=file.read_bytes();expected=json.loads(expected_bytes)
    for q in expected['questions']:
        assert call('/questions/'+str(q['index'])+'?collectionHash='+PIN)==q
        assert q['sourceQuestionIndex']==(q['index'] if q['index']<14 else q['index']-14)
    choices=[]
    for a in expected['attempts']:
        qi=a['questionIndex'];choice=dict(questionIndex=qi,reportHash=source_pin(expected['questions'][qi]),action=a['action'])
        assert call('/grade',dict(collectionHash=PIN,**choice))==a['feedback'];choices.append(choice)
    body=dict(collectionHash=PIN,choices=choices)
    assert call('/review',body)==expected
    assert call('/review/export',body,raw=True)==expected_bytes
    assert call('/review',dict(collectionHash=PIN,choices=[choices[-1]]*100))['counts']==dict(distinctQuestionsAttempted=1,withinLimit=100,outsideLimit=0,strategyDependent=0)
    assert call('/review',dict(collectionHash=PIN,choices=[]))['counts']==dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    for changed in [dict(choices[-1],questionIndex=18),dict(choices[-1],questionIndex=14.5),dict(choices[-1],reportHash='0'*64),dict(choices[-1],action='bet'),dict(choices[-1],evBb=99)]:call('/grade',dict(collectionHash=PIN,**changed),400)
    # Both sources contain AhKh, but a valid report pin from the old board is foreign here.
    call('/grade',dict(collectionHash=PIN,questionIndex=15,reportHash=source_pin(expected['questions'][11]),action='call'),400)
    call('/grade',dict(collectionHash='62bda418612e3b7c336652c960e16c1433eba3730b94b7b2cfb3e3e30cdbb619',**choices[-1]),400)
    call('/review',dict(collectionHash=PIN,choices=[choices[-1]]*101),400)
    call('/review',dict(collectionHash=PIN,choices=[None]),400)
    call('/questions/0?collectionHash='+'0'*64,None,400)
    call('/questions/18?collectionHash='+PIN,None,400)
    print('Seven-source startup,18 questions,36 grades,canonical export and strict request controls passed')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--disabled',action='store_true');run(parser.parse_args().disabled)
