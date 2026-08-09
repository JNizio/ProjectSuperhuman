#!/usr/bin/env python3
"""Validation lab for Project Superhuman scientific-engine behaviour.
No third-party packages required so CI can always run it.
"""
from statistics import mean
from math import sqrt


def classify(value, low=None, high=None):
    if low is None or high is None: return "unknown"
    if value < low: return "low"
    if value > high: return "high"
    return "normal"


def trend(values):
    if len(values) < 2: return "insufficient"
    first, last = values[0], values[-1]
    tolerance = max(1e-6, abs(first) * .01)
    if abs(last-first) <= tolerance: return "stable"
    return "rising" if last > first else "falling"


def pearson(a,b):
    if len(a) != len(b) or len(a) < 3: return None
    ma, mb = mean(a), mean(b)
    num = sum((x-ma)*(y-mb) for x,y in zip(a,b))
    den = sqrt(sum((x-ma)**2 for x in a)*sum((y-mb)**2 for y in b))
    return None if not den else num/den


def bp(sys,dia,pulse=None):
    plausible=70<=sys<=280 and 35<=dia<=180 and sys>dia and (pulse is None or 30<=pulse<=220)
    return {"plausible":plausible,"pulse_pressure":sys-dia,"map":dia+(sys-dia)/3}

assert classify(31,0,21)=="high"
assert classify(22,0,50)=="normal"
assert trend([10,10.2,11])=="rising"
assert pearson([1,2,3,4],[2,4,6,8]) > .999
assert bp(129,66,71)["plausible"]
assert bp(66,129,71)["plausible"] is False
print("Python scientific-engine validation passed")
