/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package model;

import java.util.ArrayList;
import java.util.Collection;
import thewebsemantic.Namespace;

/**
 *
 * @author Pumba
 */

@Namespace("http://code.google.com/apis/visualization/")
public class LineChart extends Visualization
{
    private String labelBinding = null;
    private Collection<String> valueBindings = new ArrayList<String>();

    public Collection<String> getValueBindings()
    {
	return valueBindings;
    }

    public void setBindings(Collection<String> bindings)
    {
	this.valueBindings = bindings;
    }

    public String getLabelBinding()
    {
	return labelBinding;
    }

    public void setLabelBinding(String labelBinding)
    {
	this.labelBinding = labelBinding;
    }
    
}
