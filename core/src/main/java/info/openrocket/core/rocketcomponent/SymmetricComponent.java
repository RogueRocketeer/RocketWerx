package info.openrocket.core.rocketcomponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import info.openrocket.core.preset.ComponentPreset;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.BoundingBox;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.MathUtil;
import static info.openrocket.core.util.MathUtil.pow2;

/**
 * Class for an axially symmetric rocket component generated by rotating
 * a function y=f(x) >= 0 around the x-axis (eg. tube, cone, etc.)
 *
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */

public abstract class SymmetricComponent extends BodyComponent implements BoxBounded, RadialParent {
	public static final double DEFAULT_RADIUS = 0.025;
	public static final double DEFAULT_THICKNESS = 0.002;

	private static final int DIVISIONS = 128; // No. of divisions when integrating

	protected boolean filled = false;
	protected double thickness = DEFAULT_THICKNESS;

	// Cached data, default values signify not calculated
	private double wetArea = Double.NaN;
	private double planArea = Double.NaN;
	private double planCenter = Double.NaN;
	protected double volume = Double.NaN;
	private double fullVolume = Double.NaN;
	protected double longitudinalUnitInertia = Double.NaN;
	protected double rotationalUnitInertia = Double.NaN;
	protected Coordinate cg = null;

	public SymmetricComponent() {
		super();
	}

	public BoundingBox getInstanceBoundingBox() {
		BoundingBox instanceBounds = new BoundingBox();

		instanceBounds.update(new Coordinate(this.getLength(), 0, 0));

		final double r = Math.max(getForeRadius(), getAftRadius());
		instanceBounds.update(new Coordinate(0, r, r));
		instanceBounds.update(new Coordinate(0, -r, -r));

		return instanceBounds;
	}

	/**
	 * Return the component radius at position x.
	 *
	 * @param x Position on x-axis.
	 * @return Radius of the component at the given position, or 0 if outside
	 *         the component.
	 */
	public abstract double getRadius(double x);

	@Override
	public abstract double getInnerRadius(double x);

	public abstract double getForeRadius();

	public abstract boolean isForeRadiusAutomatic();

	public abstract double getAftRadius();

	public abstract boolean isAftRadiusAutomatic();

	// Implement the Radial interface:
	@Override
	public final double getOuterRadius(double x) {
		return getRadius(x);
	}

	@Override
	public final double getRadius(double x, double theta) {
		return getRadius(x);
	}

	@Override
	public final double getInnerRadius(double x, double theta) {
		return getInnerRadius(x);
	}

	/**
	 * Returns the largest radius of the component (either the aft radius, or the
	 * fore radius).
	 */
	public double getMaxRadius() {
		return MathUtil.max(getForeRadius(), getAftRadius());
	}

	/**
	 * Return the component wall thickness.
	 */
	public double getThickness() {
		if (filled)
			return Math.max(getForeRadius(), getAftRadius());
		return thickness;
	}

	/**
	 * Set the component wall thickness. If <code>doClamping</code> is true, values
	 * greater than
	 * the maximum radius will be clamped the thickness to the maximum radius.
	 *
	 * @param doClamping If true, the thickness will be clamped to the maximum
	 *                   radius.
	 */
	public void setThickness(double thickness, boolean doClamping) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof SymmetricComponent) {
				((SymmetricComponent) listener).setThickness(thickness);
			}
		}

		if ((this.thickness == thickness) && !filled)
			return;
		this.thickness = doClamping ? MathUtil.clamp(thickness, 0, getMaxRadius()) : thickness;
		filled = false;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
		clearPreset();
	}

	/**
	 * Set the component wall thickness. Values greater than the maximum radius are
	 * not
	 * allowed, and will result in setting the thickness to the maximum radius.
	 */
	public void setThickness(double thickness) {
		setThickness(thickness, true);
	}

	/**
	 * Returns whether the component is set as filled. If it is set filled, then the
	 * wall thickness will have no effect.
	 */
	public boolean isFilled() {
		return filled;
	}

	@Override
	public boolean isAfter() {
		return true;
	}

	/**
	 * Sets whether the component is set as filled. If the component is filled, then
	 * the wall thickness will have no effect.
	 */
	public void setFilled(boolean filled) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof SymmetricComponent) {
				((SymmetricComponent) listener).setFilled(filled);
			}
		}

		if (this.filled == filled)
			return;
		this.filled = filled;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
		clearPreset();
	}

	/**
	 * Adds component bounds at a number of points between 0...length.
	 */
	@Override
	public Collection<Coordinate> getComponentBounds() {
		List<Coordinate> list = new ArrayList<>(20);
		for (int n = 0; n <= 5; n++) {
			double x = n * getLength() / 5;
			double r = getRadius(x);
			addBound(list, x, r);
		}
		return list;
	}

	@Override
	protected void loadFromPreset(ComponentPreset preset) {
		if (preset.has(ComponentPreset.THICKNESS)) {
			this.thickness = preset.get(ComponentPreset.THICKNESS);
			this.filled = false;
		}
		if (preset.has(ComponentPreset.FILLED)) {
			this.filled = true;
		}

		super.loadFromPreset(preset);
	}

	/**
	 * Obtain volume of component
	 *
	 * @return The volume of the component.
	 */
	@Override
	public double getComponentVolume() {
		if (Double.isNaN(volume)) {
			calculateProperties();
		}
		return volume;
	}

	/**
	 * Obtain full (filled) volume of the component
	 *
	 * @return The filled volume of the component.
	 */
	public double getFullVolume() {
		if (Double.isNaN(fullVolume)) {
			calculateProperties();
		}
		return fullVolume;
	}

	/**
	 * Obtain wetted area of the component
	 *
	 * @return The wetted area of the component.
	 */
	public double getComponentWetArea() {
		if (Double.isNaN(wetArea)) {
			calculateProperties();
		}
		return wetArea;
	}

	/**
	 * Obtain planform area of the component
	 *
	 * @return The planform area of the component.
	 */
	public double getComponentPlanformArea() {
		if (Double.isNaN(planArea)) {
			calculateProperties();
		}
		return planArea;
	}

	/**
	 * Obtain planform area of the component, defined as
	 *
	 * <pre>
	 *    integrate(x*2*r(x)) / planform area
	 * </pre>
	 *
	 * @return The planform center of the component.
	 */
	public double getComponentPlanformCenter() {
		if (Double.isNaN(planCenter)) {
			calculateProperties();
		}
		return planCenter;
	}

	/**
	 * Obtain the CG and mass of the component. Subclasses may
	 * override this method for simple shapes and use this method as necessary.
	 * Note that subclasses will typically include shoulders in their calculations
	 *
	 * @return The CG+mass of the component.
	 */
	@Override
	public Coordinate getComponentCG() {
		return getSymmetricComponentCG();
	}

	/**
	 * Obtain the CG and mass of the symmetric component. We need this method because subclasses
	 * override getComponentCG() and include mass of shoulders (if any), while moment calculations in
	 * this class assume no shoulders
	 *
	 * @return The CG+mass of the symmetric component.
	 */
	private Coordinate getSymmetricComponentCG() {
		if (cg == null)
			calculateProperties();
		return cg;
	}

	/*
	 * Obtain longitudinal unit inertia Iyy = Izz
	 *
	 * @return longitudinal unit inertia Iyy
	 */
	@Override
	public double getLongitudinalUnitInertia() {
		if (Double.isNaN(longitudinalUnitInertia)) {
			calculateProperties();
		}
		return longitudinalUnitInertia;
	}

	/*
	 * Obtain rotational unit inertia Ixx
	 *
	 * @return rotational unit inertia
	 */
	@Override
	public double getRotationalUnitInertia() {
		if (Double.isNaN(rotationalUnitInertia)) {
			calculateProperties();
		}
		return rotationalUnitInertia;
	}

	/**
	 * helper method for calculateProperties()
	 * returns a Coordinate with the CG (relative to fore end of frustum) and volume
	 * of a filled conical
	 * frustum. Result is also correct for cases of r1=r2, r1=0, and r2=0
	 * Caution! actually returns 3/PI times correct value, avoiding extra operations
	 * in loop. Gets corrected at end
	 * of numerical integration loop
	 *
	 * @param l  length (height) of frustum
	 * @param r1 radius of fore end of frustum
	 * @param r2 radius of aft end of frustum
	 * @return Coordinate containing volume (as mass) and CG of frustum
	 */
	private Coordinate calculateCG(double l, double r1, double r2) {
		final double volume = l * (pow2(r1) + r1 * r2 + pow2(r2));

		final double cg;
		if (volume < MathUtil.EPSILON) {
			cg = l / 2.0;
		} else {
			cg = l * (pow2(r1) + 2.0 * r1 * r2 + 3 * pow2(r2)) / (4.0 * (pow2(r1) + r1 * r2 + pow2(r2)));
		}

		return new Coordinate(cg, 0, 0, volume);
	}

	/**
	 * helper method for calculateProperties()
	 * returns the unit rotational moment of inertia of a solid frustum
	 * handles special case of cylinder correctly
	 * see https://web.mit.edu/8.13/8.13c/references-fall/aip/aip-handbook-section2c.pdf
	 * page 2-41, table 2c-2
	 * Caution! Returns 10/3 times the correct answer. Will need to be corrected at end
	 *
	 * @param r1 radius of fore end of frustum
	 * @param r2 radius of aft end of frustum
	 * @return rotational moment of inertia
	 */
	private double calculateUnitRotMOI(double r1, double r2) {
		// check for cylinder special case
		if (Math.abs(r1 - r2) < MathUtil.EPSILON) {
			return 10.0 * pow2(r1) / 6.0;
		}

		return (Math.pow(r2, 5) - Math.pow(r1, 5)) / (Math.pow(r2, 3) - Math.pow(r1, 3));
	}

	/**
	 * helper method for calculateLongMOI(). Computes longitudinal MOI of a cone
	 * uses 'h' for height of cone to avoid confusion with x1 and x2 in
	 * calculateProperties()
	 * requires multiplication by pi later
	 *
	 * @param h height of cone
	 * @param r radius of cone
	 * @return longitudinal moment of inertia relative to tip of cone
	 */
	private double calculateLongMOICone(double h, double r) {
		final double m = pow2(r) * h;
		final double Ixx = 3 * m * (pow2(r) / 20.0 + pow2(h) / 5.0);

		return Ixx;
	}

	/**
	 * helper method for calculateProperties(). Computes longitudinal MOI of a solid
	 * frustum relative to CG
	 * calculates by subtracting MOI of a shorter cone from MOI of longer cone
	 * more readable than deriving and using the final formula
	 * handles special case of cylinder
	 * Caution! Requires multiplication by pi later
	 *
	 * @param l  length of frustum
	 * @param r1 radius of forend of frustum
	 * @param r2 radius of aft end of frustum
	 * @param cg Coordinate with CG and mass of frustum (relative to fore end of
	 *           frustum)
	 * @return longitudinal MOI
	 */
	private double calculateLongMOI(double l, double r1, double r2, Coordinate cg) {
		// check for cylinder special case
		double moi;
		if (Math.abs(r1 - r2) < MathUtil.EPSILON) {
			// compute MOI of cylinder relative to CG of cylinder
			moi = cg.weight * (3 * pow2(r1) + pow2(l)) / 12.0;

			return moi;
		}

		// is the frustum "small end forward" or "small end aft"?
		double shiftCG = cg.x;
		if (r1 > r2) {
			final double tmp = r1;
			r1 = r2;
			r2 = tmp;
			shiftCG = l - cg.x;
		}

		// Find the heights of the two cones. Note that the h1 and h2 being calculated here
		// are NOT the x1 and x2 used in calculateProperties()
		final double h2 = l * r2 / (r2 - r1);
		final double h1 = h2 * r1 / r2;

		final double moi1 = calculateLongMOICone(h1, r1);
		final double moi2 = calculateLongMOICone(h2, r2);

		// compute MOI relative to tip of cones (they share the same tip, of course)
		moi = moi2 - moi1;

		// use parallel axis theorem to move MOI to be relative to CG of frustum.
		moi = moi - pow2(h1 + shiftCG) * cg.weight;

		return moi;
	}

	/**
	 * Performs integration over the length of the component and updates the cached
	 * variables.
	 */
	protected void calculateProperties() {
		wetArea = 0;
		planArea = 0;
		planCenter = 0;
		fullVolume = 0;
		volume = 0;
		longitudinalUnitInertia = 0;
		rotationalUnitInertia = 0;

		double cgx = 0;

		// Check length > 0
		if (getLength() <= 0) {
			return;
		}

		// Integrate for volume, CG, wetted area, planform area, and moments of inertia
		for (int n = 0; n < DIVISIONS; n++) {
			/*
			 * x1 and x2 are the bounds on this division
			 * hyp is the length of the hypotenuse from r1 to r2
			 * height is the y-axis height of the component if not filled
			 * r1o and r2o are the outer radii
			 * r1i and r2i are the inner radii
			 */

			// get x bounds and length for this division
			final double x1 = n * getLength() / DIVISIONS;
			final double x2 = (n + 1) * getLength() / DIVISIONS;
			final double l = x2 - x1;

			// get outer and inner radii
			final double r1o = getRadius(x1);
			final double r2o = getRadius(x2);

			// use thickness and angle of outer wall to get height of ring
			final double hyp = MathUtil.hypot(r2o - r1o, l);
			final double height = thickness * hyp / l;

			// get inner radii.
			final double r1i;
			final double r2i;
			if (filled) {
				r1i = 0;
				r2i = 0;
			} else {
				// Tiny inaccuracy is introduced on a division where one end is closed and other is open.
				r1i = MathUtil.max(r1o - height, 0);
				r2i = MathUtil.max(r2o - height, 0);
			}

			// find volume and CG of (possibly hollow) frustum
			final Coordinate fullCG = calculateCG(l, r1o, r2o);
			final Coordinate innerCG = calculateCG(l, r1i, r2i);

			final double dFullV = fullCG.weight;
			final double dV = fullCG.weight - innerCG.weight;
			final double dCG = (fullCG.x * fullCG.weight - innerCG.x * innerCG.weight) / dV;

			// First moment, used later for CG calculation
			final double dCGx = dV * (x1 + dCG);

			// rotational moment of inertia
			final double Ixxo = calculateUnitRotMOI(r1o, r2o);
			final double Ixxi = calculateUnitRotMOI(r1i, r2i);
			final double Ixx = Ixxo * fullCG.weight - Ixxi * innerCG.weight;

			// longitudinal moment of inertia -- axis through CG of division
			double Iyy = calculateLongMOI(l, r1o, r2o, fullCG) - calculateLongMOI(l, r1i, r2i, innerCG);

			// move to axis through forward end of component
			Iyy += dV * pow2(x1 + dCG);

			// Add to the volume-related components
			volume += dV;
			fullVolume += dFullV;
			cgx += dCGx;
			rotationalUnitInertia += Ixx;
			longitudinalUnitInertia += Iyy;

			// Wetted area ( * PI at the end)
			wetArea += (r1o + r2o) * Math.sqrt(pow2(r1o - r2o) + pow2(l));

			// Planform area & moment
			final double dA = l * (r1o + r2o);
			planArea += dA;
			final double planMoment = dA * x1 + 2.0 * pow2(l) * (r1o / 6.0 + r2o / 3.0);
			planCenter += planMoment;
		}

		if (planArea > 0)
			planCenter /= planArea;

		// get unit moments of inertia
		rotationalUnitInertia /= volume;
		longitudinalUnitInertia /= volume;

		// Correct for deferred constant factors
		volume *= Math.PI / 3.0;
		fullVolume *= Math.PI / 3.0;
		cgx *= Math.PI / 3.0;
		wetArea *= Math.PI;
		rotationalUnitInertia *= 3.0 / 10.0;

		if (volume < 0.0000000001) { // 0.1 mm^3
			volume = 0;
			cg = new Coordinate(getLength() / 2, 0, 0, 0);
		} else {
			// the mass of this shape is the material density * volume.
			// it cannot come from super.getComponentMass() since that
			// includes the shoulders
			cg = new Coordinate(cgx / volume, 0, 0, getMaterial().getDensity() * volume);
		}

		// a component so small it has no volume can't contribute to moment of inertia
		if (MathUtil.equals(volume, 0)) {
			rotationalUnitInertia = 0;
			longitudinalUnitInertia = 0;
			return;
		}

		// Shift longitudinal inertia to CG
		longitudinalUnitInertia = longitudinalUnitInertia - pow2(cg.x);

	}

	/**
	 * Invalidates the cached volume and CG information.
	 */
	@Override
	protected void componentChanged(ComponentChangeEvent e) {
		super.componentChanged(e);
		if (e.isAerodynamicChange() || e.isMassChange()) {
			wetArea = Double.NaN;
			planArea = Double.NaN;
			planCenter = Double.NaN;
			volume = Double.NaN;
			fullVolume = Double.NaN;
			longitudinalUnitInertia = Double.NaN;
			rotationalUnitInertia = Double.NaN;
			cg = null;
		}
	}

	/////////// Auto radius helper methods

	/**
	 * Returns the automatic radius for this component towards the
	 * front of the rocket. The automatics will not search towards the
	 * rear of the rocket for a suitable radius. A positive return value
	 * indicates a preferred radius, a negative value indicates that a
	 * match was not found.
	 */
	protected abstract double getFrontAutoRadius();

	/**
	 * Returns the automatic radius for this component towards the
	 * end of the rocket. The automatics will not search towards the
	 * front of the rocket for a suitable radius. A positive return value
	 * indicates a preferred radius, a negative value indicates that a
	 * match was not found.
	 */

	protected abstract double getRearAutoRadius();

	/**
	 * Return the previous symmetric component, or null if none exists.
	 *
	 * @return the previous SymmetricComponent, or null.
	 */
	public final SymmetricComponent getPreviousSymmetricComponent() {
		if ((null == this.parent) || (null == this.parent.getParent())) {
			return null;
		}

		// might be: (a) Rocket -- for Centerline/Axial stages
		// (b) BodyTube -- for Parallel Stages & PodSets
		final RocketComponent grandParent = this.parent.getParent();

		int searchParentIndex = grandParent.getChildPosition(this.parent); // position of component w/in parent
		int searchSiblingIndex = this.parent.getChildPosition(this) - 1; // guess at index of previous component

		while (0 <= searchParentIndex) {
			final RocketComponent searchParent = grandParent.getChild(searchParentIndex);

			if (searchParent instanceof ComponentAssembly) {
				while (0 <= searchSiblingIndex) {
					final RocketComponent searchSibling = searchParent.getChild(searchSiblingIndex);
					if ((searchSibling instanceof SymmetricComponent) && inline(searchSibling)) {
						return getPreviousSymmetricComponentFromComponentAssembly((SymmetricComponent) searchSibling,
								(SymmetricComponent) searchSibling, 0);
					}
					--searchSiblingIndex;
				}
			}

			// Look forward to the previous stage
			--searchParentIndex;

			if (0 <= searchParentIndex) {
				searchSiblingIndex = grandParent.getChild(searchParentIndex).getChildCount() - 1;
			}
		}

		// one last thing -- I could be the child of a ComponentAssembly, and in line with
		// the SymmetricComponent that is my grandParent
		if ((grandParent instanceof SymmetricComponent) && inline(grandParent)) {
			// If the grandparent is actually before me, then this is the previous component
			if ((parent.getAxialOffset(AxialMethod.TOP) + getAxialOffset(AxialMethod.TOP)) > 0) {
				return (SymmetricComponent) grandParent;
			}
			// If not, then search for the component before the grandparent
			else {
				// NOTE: will be incorrect if the ComponentAssembly is even further to the front than the
				// previous component of the grandparent. But that would be really bad rocket design...
				return ((SymmetricComponent) grandParent).getPreviousSymmetricComponent();
			}
		}

		return null;
	}

	/**
	 * Checks if parent has component assemblies that have potential previous components.
	 * A child symmetric component of a component assembly is a potential previous component if:
	 * 	- it is inline with the parent
	 * 	- it is flush with the end of the parent
	 * 	- it is larger in aft radius than the parent
	 * @param parent parent component to check for child component assemblies
	 * @param previous the current previous component candidate
	 * @param flushOffset an extra offset to be added to check for flushness. This is used when recursively running this
	 *                    method to check children of children of the original parent are flush with the end of the
	 *                    original parent.
	 * @return the previous component if it is found
	 */
	private SymmetricComponent getPreviousSymmetricComponentFromComponentAssembly(SymmetricComponent parent,
																				  SymmetricComponent previous, double flushOffset) {
		if (previous == null) {
			return parent;
		}
		if (parent == null) {
			return previous;
		}

		double maxRadius = previous.isAftRadiusAutomatic() ? 0 : previous.getAftRadius();
		SymmetricComponent previousComponent = previous;
		for (ComponentAssembly assembly : parent.getDirectChildAssemblies()) {
			if (assembly.getChildCount() == 0) {
				continue;
			}
			/*
			 * Check if the component assembly's last child is a symmetric component that is:
			 *	- inline with the parent
			 * 	- flush with the end of the parent
			 * 	- larger in aft radius than the parent
			 * in that case, this component assembly is the new previousComponent.
			 */
			RocketComponent lastChild = assembly.getChild(assembly.getChildCount() - 1);
			if (!((lastChild instanceof SymmetricComponent) && parent.inline(lastChild))) {
				continue;
			}
			SymmetricComponent lastSymmetricChild = (SymmetricComponent) lastChild;
			double flushDeviation = flushOffset + assembly.getAxialOffset(AxialMethod.BOTTOM); // How much the last
			// child is flush with
			// the parent

			// If the last symmetric child from the assembly if flush with the end of the
			// parent and larger than the
			// current previous component, then this is the new previous component
			if (MathUtil.equals(flushDeviation, 0) && !lastSymmetricChild.isAftRadiusAutomatic() &&
					lastSymmetricChild.getAftRadius() > maxRadius) {
				previousComponent = lastSymmetricChild;
				maxRadius = previousComponent.getAftRadius();
			}
			// It could be that there is a child component assembly that is flush with the
			// end of the parent or larger
			// Recursively check assembly's children
			previousComponent = getPreviousSymmetricComponentFromComponentAssembly(lastSymmetricChild,
					previousComponent, flushDeviation);
			if (previousComponent != null && !previousComponent.isAftRadiusAutomatic()) {
				maxRadius = previousComponent.getAftRadius();
			}
		}

		return previousComponent;
	}

	/**
	 * Return the next symmetric component, or null if none exists.
	 *
	 * @return the next SymmetricComponent, or null.
	 */
	public final SymmetricComponent getNextSymmetricComponent() {
		if ((null == this.parent) || (null == this.parent.getParent())) {
			return null;
		}

		// might be: (a) Rocket -- for centerline stages
		// (b) BodyTube -- for Parallel Stages
		final RocketComponent grandParent = this.parent.getParent();

		// note: this is not guaranteed to _contain_ a stage... but that we're
		// _searching_ for one.
		int searchParentIndex = grandParent.getChildPosition(this.parent);
		int searchSiblingIndex = this.parent.getChildPosition(this) + 1;

		while (searchParentIndex < grandParent.getChildCount()) {
			final RocketComponent searchParent = grandParent.getChild(searchParentIndex);

			if (searchParent instanceof ComponentAssembly) {
				while (searchSiblingIndex < searchParent.getChildCount()) {
					final RocketComponent searchSibling = searchParent.getChild(searchSiblingIndex);
					if ((searchSibling instanceof SymmetricComponent) && inline(searchSibling)) {
						return getNextSymmetricComponentFromComponentAssembly((SymmetricComponent) searchSibling,
								(SymmetricComponent) searchSibling, 0);
					}
					++searchSiblingIndex;
				}
			}

			// Look aft to the next stage
			++searchParentIndex;
			searchSiblingIndex = 0;
		}

		// One last thing -- I could have a child that is a ComponentAssembly that is in line
		// with me
		for (RocketComponent child : getChildren()) {
			if (child instanceof ComponentAssembly) {
				for (RocketComponent grandchild : child.getChildren()) {
					if ((grandchild instanceof SymmetricComponent) && inline(grandchild)) {
						// If the grandparent is actually after me, then this is the next component
						if ((parent.getAxialOffset(AxialMethod.BOTTOM) + getAxialOffset(AxialMethod.BOTTOM)) < 0) {
							return (SymmetricComponent) grandchild;
						}
						// If not, then search for the component after the grandparent
						else {
							// NOTE: will be incorrect if the ComponentAssembly is even further to the back than the
							// next component of the grandparent. But that would be really bad rocket design...
							return ((SymmetricComponent) grandchild).getNextSymmetricComponent();
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * Checks if parent has component assemblies that have potential next components.
	 * A child symmetric component of a component assembly is a potential next component if:
	 * 	- it is inline with the parent
	 * 	- it is flush with the front of the parent
	 * 	- it is larger in fore radius than the parent
	 * @param parent parent component to check for child component assemblies
	 * @param next the next previous component candidate
	 * @param flushOffset an extra offset to be added to check for flushness. This is used when recursively running this
	 *                    method to check children of children of the original parent are flush with the front of the
	 *                    original parent.
	 * @return the next component if it is found
	 */
	private SymmetricComponent getNextSymmetricComponentFromComponentAssembly(SymmetricComponent parent,
																			  SymmetricComponent next, double flushOffset) {
		if (next == null) {
			return parent;
		}
		if (parent == null) {
			return next;
		}

		double maxRadius = next.isForeRadiusAutomatic() ? 0 : next.getForeRadius();
		SymmetricComponent nextComponent = next;
		for (ComponentAssembly assembly : parent.getDirectChildAssemblies()) {
			if (assembly.getChildCount() == 0) {
				continue;
			}
			/*
			 * Check if the component assembly's last child is a symmetric component that
			 * is:
			 * - inline with the parent
			 * - flush with the front of the parent
			 * - larger in fore radius than the parent
			 * in that case, this component assembly is the new nextComponent.
			 */
			RocketComponent firstChild = assembly.getChild(0);
			if (!((firstChild instanceof SymmetricComponent) && parent.inline(firstChild))) {
				continue;
			}
			SymmetricComponent firstSymmetricChild = (SymmetricComponent) firstChild;
			double flushDeviation = flushOffset + assembly.getAxialOffset(AxialMethod.TOP); // How much the last child
			// is flush with the parent

			// If the first symmetric child from the assembly if flush with the front of the
			// parent and larger than the
			// current next component, then this is the new next component
			if (MathUtil.equals(flushDeviation, 0) && !firstSymmetricChild.isForeRadiusAutomatic() &&
					firstSymmetricChild.getForeRadius() > maxRadius) {
				nextComponent = firstSymmetricChild;
				maxRadius = nextComponent.getForeRadius();
			}
			// It could be that there is a child component assembly that is flush with the
			// front of the parent or larger
			// Recursively check assembly's children
			nextComponent = getNextSymmetricComponentFromComponentAssembly(firstSymmetricChild, nextComponent,
					flushDeviation);
			if (nextComponent != null && !nextComponent.isForeRadiusAutomatic()) {
				maxRadius = nextComponent.getForeRadius();
			}
		}

		return nextComponent;
	}

	/***
	 * Determine whether a candidate symmetric component is in line with us
	 *
	 */
	private boolean inline(final RocketComponent candidate) {
		// if we share a parent, we are in line
		if (this.parent == candidate.parent)
			return true;

		// if both of our parents are either not ring instanceable, or
		// have a radial offset of 0 from their centerline, we are in line.

		if ((this.parent instanceof RingInstanceable) &&
				(!MathUtil.equals(this.parent.getRadiusMethod().getRadius(
						this.parent.parent, this, this.parent.getRadiusOffset()), 0)))
			return false;

		if (candidate.parent instanceof RingInstanceable) {
			// We need to check if the grandparent of the candidate is a body tube and if
			// the outer radius is automatic.
			// If so, then this would cause an infinite loop when checking the radius of the
			// radiusMethod.
			if (candidate.parent.parent == this && (this.isAftRadiusAutomatic() || this.isForeRadiusAutomatic())) {
				return false;
			} else {
				return MathUtil.equals(candidate.parent.getRadiusMethod().getRadius(
						candidate.parent.parent, candidate, candidate.parent.getRadiusOffset()), 0);
			}
		}

		return true;
	}

	/**
	 * Checks whether the component uses the previous symmetric component for its
	 * auto diameter.
	 */
	public abstract boolean usesPreviousCompAutomatic();

	/**
	 * Checks whether the component uses the next symmetric component for its auto
	 * diameter.
	 */
	public abstract boolean usesNextCompAutomatic();

}
