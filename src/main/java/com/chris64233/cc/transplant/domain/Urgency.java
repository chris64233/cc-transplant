package com.chris64233.cc.transplant.domain;

/**
 * 紧急等级，排序时权重从高到低：URGENT &gt; HIGH &gt; STANDARD。
 */
public enum Urgency {
    STANDARD(0),
    HIGH(1),
    URGENT(2);

    private final int weight;

    Urgency(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
