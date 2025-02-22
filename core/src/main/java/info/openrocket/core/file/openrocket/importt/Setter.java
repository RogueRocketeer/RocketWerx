package info.openrocket.core.file.openrocket.importt;

import java.util.HashMap;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.RocketComponent;

////  Interface
interface Setter {
	/**
	 * Set the specified value to the given component.
	 * 
	 * @param component  the component to which to set.
	 * @param value      the value within the element.
	 * @param attributes attributes for the element.
	 * @param warnings   the warning set to use.
	 */
	public void set(RocketComponent component, String value,
			HashMap<String, String> attributes, WarningSet warnings);
}