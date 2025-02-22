package info.openrocket.core.optimization.rocketoptimization.domains;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.optimization.rocketoptimization.SimulationDomain;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.unit.Value;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Pair;

/**
 * A simulation domain that limits the required stability of the rocket.
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class StabilityDomain implements SimulationDomain {

	private final double minimum;
	private final boolean minAbsolute;
	private final double maximum;
	private final boolean maxAbsolute;

	/**
	 * Sole constructor.
	 * 
	 * @param minimum     minimum stability requirement (or <code>NaN</code> for no
	 *                    limit)
	 * @param minAbsolute <code>true</code> if minimum is an absolute SI
	 *                    measurement,
	 *                    <code>false</code> if it is relative to the rocket caliber
	 * @param maximum     maximum stability requirement (or <code>NaN</code> for no
	 *                    limit)
	 * @param maxAbsolute <code>true</code> if maximum is an absolute SI
	 *                    measurement,
	 *                    <code>false</code> if it is relative to the rocket caliber
	 */
	public StabilityDomain(double minimum, boolean minAbsolute, double maximum, boolean maxAbsolute) {
		super();
		this.minimum = minimum;
		this.minAbsolute = minAbsolute;
		this.maximum = maximum;
		this.maxAbsolute = maxAbsolute;
	}

	@Override
	public Pair<Double, Value> getDistanceToDomain(Simulation simulation) {
		Coordinate cp, cg;
		double cpx, cgx;
		double absolute;
		double relative;

		/*
		 * These are instantiated each time because this class must be thread-safe.
		 * Caching would in any case be inefficient since the rocket changes all the
		 * time.
		 */
		AerodynamicCalculator aerodynamicCalculator = new BarrowmanCalculator();

		FlightConfiguration configuration = simulation.getActiveConfiguration();
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setMach(Application.getPreferences().getDefaultMach());
		conditions.setAOA(0);
		conditions.setRollRate(0);

		// TODO: HIGH: This re-calculates the worst theta value every time
		cp = aerodynamicCalculator.getWorstCP(configuration, conditions, null);
		cg = MassCalculator.calculateLaunch(configuration).getCM();

		if (cp.weight > 0.000001)
			cpx = cp.x;
		else
			cpx = Double.NaN;

		if (cg.weight > 0.000001)
			cgx = cg.x;
		else
			cgx = Double.NaN;

		// Calculate the reference (absolute or relative)
		absolute = cpx - cgx;

		double diameter = 0;
		for (RocketComponent c : configuration.getActiveComponents()) {
			if (c instanceof SymmetricComponent) {
				double d1 = ((SymmetricComponent) c).getForeRadius() * 2;
				double d2 = ((SymmetricComponent) c).getAftRadius() * 2;
				diameter = MathUtil.max(diameter, d1, d2);
			}
		}
		relative = absolute / diameter;

		Value desc;
		if (minAbsolute && maxAbsolute) {
			desc = new Value(absolute, UnitGroup.UNITS_LENGTH);
		} else {
			desc = new Value(relative, UnitGroup.UNITS_STABILITY_CALIBERS);
		}

		double ref;
		if (minAbsolute) {
			ref = minimum - absolute;
			if (ref > 0) {
				return new Pair<>(ref, desc);
			}
		} else {
			ref = minimum - relative;
			if (ref > 0) {
				return new Pair<>(ref, desc);
			}
		}

		if (maxAbsolute) {
			ref = absolute - maximum;
			if (ref > 0) {
				return new Pair<>(ref, desc);
			}
		} else {
			ref = relative - maximum;
			if (ref > 0) {
				return new Pair<>(ref, desc);
			}
		}

		return new Pair<>(0.0, desc);
	}
}
