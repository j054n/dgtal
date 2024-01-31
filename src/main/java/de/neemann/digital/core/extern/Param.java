/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.core.extern;

import de.neemann.digital.core.Bits;
import de.neemann.digital.hdl.hgs.HGSMap;

/**
 * A param for external access
 */
public class Param implements HGSMap {
    private final int bits;
    private final int val;
    private final String name;


    /**
     * Creates a new param
     *
     * @param name the name
     * @param bits the number of bits
     */
    public Param(String name, int bits, int val) {
        this.name = name;
        this.bits = bits;
        this.val = val;
    }

    /**
     * Creates a new param
     *
     * @param param the param
     */
    public Param(String param) {
        val = 0;
        int p = param.indexOf(':');
        if (p < 0) {
            name = param;
            bits = 32;
        } else {
            name = param.substring(0, p);
            int b = 1;
            try {
                b = (int) Bits.decode(param.substring(p + 1));
            } catch (Bits.NumberFormatException e) {
                b = 32;
            }
            bits = b;
        }
    }

    /**
     * @return the number of bits
     */
    public int getBits() {
        return bits;
    }

    /**
     * @return the value of parameter
     */
    public int getVal() {
        return val;
    }
    /**
     * @return the name of the parameter
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        if (bits == 1)
            return name;
        else
            return name + ":" + bits;
    }

    @Override
    public Object hgsMapGet(String key) {
        switch (key) {
            case "name":
                return name;
            case "bits":
                return bits;
            default:
                return null;
        }
    }
}
