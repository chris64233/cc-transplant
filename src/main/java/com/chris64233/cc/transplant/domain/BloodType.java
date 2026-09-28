package com.chris64233.cc.transplant.domain;

/**
 * ABO 血型。分配时按供体血型检查候选受者的相容性。
 */
public enum BloodType {
    O,
    A,
    B,
    AB;

    /**
     * 判断供体血型是否可输注给受者：
     * O 为万能供体；AB 为万能受者；其余要求同型。
     */
    public boolean compatibleWith(BloodType recipient) {
        if (recipient == null) {
            return false;
        }
        if (this == O) {
            return true;
        }
        if (recipient == AB) {
            return true;
        }
        return this == recipient;
    }
}
