package com.chris64233.cc.transplant.domain;

/**
 * ABO 血型。
 */
public enum BloodType {
    A,
    B,
    AB,
    O;

    /**
     * 供者血型是否可以捐献给受者血型（ABO 相容性）。
     *
     * <p>O 为万能供血者，AB 为万能受血者。</p>
     */
    public boolean canDonateTo(BloodType recipient) {
        return switch (this) {
            case O -> true;
            case A -> recipient == A || recipient == AB;
            case B -> recipient == B || recipient == AB;
            case AB -> recipient == AB;
        };
    }
}
