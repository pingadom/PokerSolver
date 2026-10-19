import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('refined_drill_check',ROOT/'scripts/check-refined-flop-drill.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
INPUT=ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-request.json'
REPORT=ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-report.json.gz'
DRILL=ROOT/'docs/data/supplied-paired-btn-facing-bet-drill.json'

class RefinedDrillTest(unittest.TestCase):
    def test_saved_review_matches_independent_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-paired-btn-facing-bet-drill-independent-check.json').read_text())
        self.assertEqual(expected,checker.check(INPUT,REPORT,DRILL))
    def test_question_grades_counts_and_limits_are_owned(self):
        original=json.loads(DRILL.read_text())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in ['hero','board','pin','admission','context','index','action','grade','alternatives','count','oversize','extra']:
                v=copy.deepcopy(original)
                if kind=='hero':v['question']['heroCards']='Ah Kh'
                elif kind=='board':v['question']['context']['board'][0]='8s'
                elif kind=='pin':v['question']['reportHash']='0'*64
                elif kind=='admission':v['question']['trainerAdmission']=True
                elif kind=='context':v['question']['context']['toCallBb']=0
                elif kind=='index':v['attempts'][0]['index']=True
                elif kind=='action':v['attempts'][0]['action']='check'
                elif kind=='grade':v['attempts'][0]['feedback']['selected']['lowerLossBb']=0
                elif kind=='alternatives':v['attempts'][0]['feedback']['alternatives']=[]
                elif kind=='count':v['counts']['withinLimit']=True
                elif kind=='oversize':v['attempts']*=51
                elif kind=='extra':v['accuracy']=100
                path.write_text(json.dumps(v),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(INPUT,REPORT,path)
            path.write_bytes(b'x'*(checker.LIMIT+1))
            with self.assertRaises(ValueError):checker.read(path)

if __name__=='__main__':unittest.main()
