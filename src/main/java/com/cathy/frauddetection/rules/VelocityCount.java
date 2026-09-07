package com.cathy.frauddetection.rules;

// A Drools fact, not a domain concept: Drools matches objects in working
// memory, so the velocity numbers must become an object before the DRL can
// see them. SimpleRuleEvaluator takes the same two values as plain arguments
// and needs no equivalent.
//
// limit travels with count so the DRL can compare field against field. That
// keeps the threshold in one place — application.yml — instead of repeating
// it as a literal in the DRL, which is what the amount rule still does.
record VelocityCount(long count, long limit) {
}