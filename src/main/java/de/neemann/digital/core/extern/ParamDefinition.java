/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.core.extern;

import de.neemann.digital.core.ObservableValue;
import de.neemann.digital.core.ObservableValues;
import de.neemann.digital.core.element.PinDescription;
import de.neemann.digital.core.element.PinDescriptions;
import de.neemann.digital.core.element.PinInfo;
import de.neemann.digital.hdl.hgs.HGSArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * The list of ports used by an external component
 */
public class ParamDefinition implements Iterable<Param>, HGSArray {
    private final ArrayList<Param> params;

    /**
     * Creates a new instance
     *
     * @param paramDescription a comma separated list of param names
     */
    public ParamDefinition(String paramDescription) {
        params = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(paramDescription, ",");
        while (st.hasMoreTokens())
            params.add(new Param(st.nextToken().trim()));
    }

    /**
     * Creates the output values to be used by the model
     *
     * @return the output values
     */
    public ObservableValues createOutputs() {
        ObservableValues.Builder builder = new ObservableValues.Builder();
        for (Param p : params)
            builder.add(new ObservableValue(p.getName(), p.getBits()));
        return builder.build();
    }

    /**
     * Creates the pin descriptions
     *
     * @param direction the direction to use for the pin descriptions
     * @return the pin descriptions
     */
    public PinDescriptions getPinDescriptions(PinDescription.Direction direction) {
        PinInfo[] infos = new PinInfo[params.size()];
        for (int i = 0; i < infos.length; i++)
            infos[i] = new PinInfo(params.get(i).getName(), "", direction);
        return new PinDescriptions(infos);
    }

    /**
     * Getter for a single param
     *
     * @param i the param number
     * @return the param
     */
    public Param getParam(int i) {
        return params.get(i);
    }

    /**
     * Gets param value
     *
     * @param name the name
     * @return the value of param
     */
    public int getParamVal(String name) {
        int val = 0;
        for (Param p : params) {
            if (p.getName().equals(name)) {
                val = p.getVal();
                break;
            }
        }
        return val;
    }

    /**
     * @return sum of all bits
     */
    public int getBits() {
        int bits = 0;
        for (Param p : params)
            bits += p.getBits();
        return bits;
    }

    @Override
    public Iterator<Param> iterator() {
        return params.iterator();
    }

    /**
     * Adds a param to this description
     *
     * @param name the name
     * @param bits the number of bits
     */
    public void addParam(String name, int bits, int val) {
        params.add(new Param(name, bits, val));
    }

    /**
     * @return the number of params
     */
    public int size() {
        return params.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Param p : params) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(p.toString());
        }

        return sb.toString();
    }

    @Override
    public int hgsArraySize() {
        return params.size();
    }

    @Override
    public Object hgsArrayGet(int i) {
        return params.get(i);
    }
}
