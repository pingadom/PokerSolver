"""Default absence or real eight-source startup, twenty questions, every move and exact export."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

PREFIX='/api/v1/trainer/research/flop-interval-collection-v7'
BASE='http://localhost:18080'+PREFIX
PIN='eeeadc2a2addf1e6878af533fe51251a42f3f2afa2f1a1fb7f0dd6e6bb01b9a5'
STARTUP_SECONDS=1500

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
        print('Twenty-question routes absent without opt-in');return
    metadata=wait_for_metadata()
    assert metadata['collectionHash']==PIN and metadata['availableQuestions']==20 and metadata['activePlayers']==2
    assert metadata['independentInputSets']==2 and metadata['trainerAdmission'] is False and metadata['maximumAttempts']==100
    assert metadata['startupBudget']==dict(sourceReplays=8,freshReferenceRuns=16,freshIterations=1452000)
    assert metadata['addedReferenceSharing']=='SEPARATE_BET_SIZE_GAME_TWO_FRESH_POLICIES_BOTH_BB_QUESTIONS_CHECKED/v1'
    assert metadata['questionKinds']==['LEGACY_EIGHTEEN_QUALIFIED_COLLECTION']*18+['POT_SIZE_BIG_BLIND_QUESTIONS']*2
    docs_url=BASE.removesuffix(PREFIX)+'/v3/api-docs'
    with urllib.request.urlopen(docs_url,timeout=20) as response:docs=json.load(response)
    schemas=docs['components']['schemas']
    for route,name,fields in [('grade','SizingFlopV7GradeRequest',{'collectionHash','questionIndex','reportHash','action'}),('review','SizingFlopV7ReviewRequest',{'collectionHash','choices'}),('review/export','SizingFlopV7ReviewRequest',{'collectionHash','choices'})]:
        request=docs['paths'][PREFIX+'/'+route]['post']['requestBody'];assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref']=='#/components/schemas/'+name
        assert set(schemas[name]['properties'])==fields
    assert schemas['SizingFlopV7ReviewRequest']['properties']['choices']['items']['$ref']=='#/components/schemas/SizingFlopV7SubmittedChoice'
    file=Path(__file__).resolve().parents[1]/'docs/data/supplied-twenty-question-flop-collection-review.json'
    expected_bytes=file.read_bytes();expected=json.loads(expected_bytes)
    for q in expected['questions']:
        assert call('/questions/'+str(q['index'])+'?collectionHash='+PIN)==q
        assert q['sourceQuestionIndex']==(q['index'] if q['index']<18 else q['index']-18)
    for i in (18,19):
        table=expected['questions'][i]['decision']['pot']['table']
        assert table['actingSeat']=='BB' and table['potBb']==13 and table['toCallBb']==table['betSizeBb']==6.5
    choices=[]
    for a in expected['attempts']:
        qi=a['questionIndex'];choice=dict(questionIndex=qi,reportHash=source_pin(expected['questions'][qi]),action=a['action'])
        assert call('/grade',dict(collectionHash=PIN,**choice))==a['feedback'];choices.append(choice)
    body=dict(collectionHash=PIN,choices=choices)
    assert call('/review',body)==expected
    assert call('/review/export',body,raw=True)==expected_bytes
    assert call('/review',dict(collectionHash=PIN,choices=[choices[-2]]*100))['counts']==dict(distinctQuestionsAttempted=1,withinLimit=100,outsideLimit=0,strategyDependent=0)
    assert call('/review',dict(collectionHash=PIN,choices=[]))['counts']==dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    for changed in [dict(choices[-1],questionIndex=20),dict(choices[-1],questionIndex=18.5),dict(choices[-1],reportHash='0'*64),dict(choices[-1],action='bet'),dict(choices[-1],evBb=99)]:call('/grade',dict(collectionHash=PIN,**changed),400)
    # The same public board cannot transfer a report pin across betting games.
    call('/grade',dict(collectionHash=PIN,questionIndex=18,reportHash=source_pin(expected['questions'][15]),action='call'),400)
    call('/grade',dict(collectionHash='b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2',**choices[-1]),400)
    call('/review',dict(collectionHash=PIN,choices=[choices[-1]]*101),400)
    call('/review',dict(collectionHash=PIN,choices=[None]),400)
    call('/questions/0?collectionHash='+'0'*64,None,400)
    call('/questions/20?collectionHash='+PIN,None,400)
    print('Eight-source startup,20 questions,40 grades,canonical export and strict request controls passed')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--disabled',action='store_true');run(parser.parse_args().disabled)
