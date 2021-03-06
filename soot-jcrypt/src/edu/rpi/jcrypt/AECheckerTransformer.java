package edu.rpi.jcrypt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.BodyTransformer;
import soot.BooleanType;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.StaticInvokeExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

import static com.esotericsoftware.minlog.Log.*;

public class AECheckerTransformer extends BodyTransformer {

	private Set<String> polyValues;
	private Map<String, Set<String>> detContainers = new HashMap<>();
	private Map<String, Byte> encryptions = new HashMap<>();
	private Map<String, Byte> aeResults;
	private Set<String> conversions = new HashSet<>();
	private SimpleLocalDefs defs;
	private UnitGraph cfg;
	private Map<String, String[]> mapreduceClasses = JCryptTransformer.mapreduceClasses;

	public Map<String, Byte> getEncryptions() {
		return encryptions;
	}

	public AECheckerTransformer(Map<String, Byte> map, Set<String> polyValues) {
		info(this.getClass().getSimpleName(), "Checking conversions ...");
		this.aeResults = map;
		this.polyValues = polyValues;
		Set<String> methods = new HashSet<>();
		methods.add("contains");
		detContainers.put("java.util.ArrayList", methods);
		methods = new HashSet<>();
		methods.add("contains");
		methods.add("add");
		detContainers.put("java.util.HashSet", methods);
		detContainers.put("java.util.TreeSet", methods);
		methods = new HashSet<>();
		methods.add("containsKey");
		methods.add("put");
		detContainers.put("java.util.HashMap", methods);
		detContainers.put("java.util.LinkedHashMap", methods);
		methods = new HashSet<>();
		methods.add("equals");
		detContainers.put("java.lang.String", methods);
//		methods = new HashSet<>();
//		methods.add("write");
//		detContainers.put("org.apache.hadoop.mapreduce.Mapper$Context", methods);
//		methods = new HashSet<>();
//		methods.add("collect");
//		detContainers.put("org.apache.hadoop.mapred.OutputCollector", methods);
	}

	public Set<String> getConversions() {
		return conversions;
	}

	@Override
	protected synchronized void internalTransform(Body body, String phaseName, @SuppressWarnings("rawtypes") Map options) {
		cfg = new BriefUnitGraph(body);
		defs = new SimpleLocalDefs(cfg);
		for (Unit unit : body.getUnits()) {
			if (unit instanceof AssignStmt) {
				// Get operands
				Value lhsOp = ((AssignStmt) unit).getLeftOp();
				Value rhsOp = ((AssignStmt) unit).getRightOp();
				if (rhsOp instanceof BinopExpr) { // x = y + z
					String symbol = ((BinopExpr) rhsOp).getSymbol();
					Value op1 = ((BinopExpr) rhsOp).getOp1();
					Value op2 = ((BinopExpr) rhsOp).getOp2();
					checkCondition(symbol, op1, op2, unit, body.getMethod());
					switch (symbol) {
					case " + ":
					case " - ":
						checkConversion(op1, (byte) 0b100, unit, body.getMethod());
						checkConversion(op2, (byte) 0b100, unit, body.getMethod());
						break;
					case " / ":
					case " % ":
					case " >> ":
					case " >>> ":
						// no encryption schemes for these operations currently,
						// so need conversions
						checkConversion(op1, unit, body.getMethod());
						checkConversion(op2, unit, body.getMethod());
					}
				} else if (rhsOp instanceof StaticInvokeExpr) { // Math.floor()
					SootMethod method = ((StaticInvokeExpr) rhsOp).getMethod();
					String className = method.getDeclaringClass().getName();
					if (className.equals("java.lang.Math"))
						checkConversion(lhsOp, unit, body.getMethod());

				}
			} else if (unit instanceof IfStmt) {
				Value condition = ((IfStmt) unit).getCondition();
				if (condition instanceof BinopExpr) {
					Value op1 = ((BinopExpr) condition).getOp1();
					Value op2 = ((BinopExpr) condition).getOp2();
					checkCondition(((BinopExpr) condition).getSymbol(), op1, op2, unit, body.getMethod());
				}
			} else if (unit instanceof InvokeStmt) {
				handleInvokeExpr(body.getMethod(), unit);
			}
		}
	}

	private void handleInvokeExpr(SootMethod sm, Unit unit) {
		InvokeExpr ie = ((InvokeStmt) unit).getInvokeExpr();
		SootMethod method = ie.getMethod();
		String className = method.getDeclaringClass().getName();
		if (ie.getArgCount() == 0) return;
		Value arg0 = ie.getArg(0);
		if (method.isStatic()) {
			if ((className.equals("java.util.Collections") || className.equals("java.util.Arrays"))
					&& method.getName().equals("sort"))
				checkConversion(arg0, (byte) 0b1, unit, sm);
		} else {
			if (detContainers.containsKey(className))
				if (detContainers.get(className).contains(method.getName()))
					checkConversion(arg0, (byte) 0b10, unit, sm);
			String[] info = mapreduceClasses.get(className);
			if (info != null && info[1].contains("m"))
				checkConversion(arg0, (byte) 0b10, unit, sm);
		}
	}
	
	private void checkConversion(Value v, Unit unit, SootMethod sm) {
		String id = TransUtils.getIdenfication(v, sm);
		if (polyValues.contains(id))
			conversions.add(sm.getDeclaringClass().getName() + ": " + unit.toString());
	}

	private void checkCondition(String symbol, Value op1, Value op2, Unit unit, SootMethod sm) {
		switch (symbol) {
		case " == ":
		case " != ":
		case " * ":
		case " << ":
			checkConversion(op1, (byte) 0b10, unit, sm);
			checkConversion(op2, (byte) 0b10, unit, sm);
			break;
		case " cmp ":
		case " cmpg ":
		case " cmpl ":
		case " < ":
		case " > ":
		case " >= ":
		case " <= ":
			checkConversion(op1, (byte) 0b1, unit, sm);
			checkConversion(op2, (byte) 0b1, unit, sm);
		}
	}

	private void checkConversion(Value v, byte type, Unit unit, SootMethod sm) {
		if (v.getType() instanceof BooleanType) 
			return;
		String id = TransUtils.getIdenfication(v, sm);
		if (polyValues.contains(id)) {
			if ((aeResults.getOrDefault(id, (byte) 0b1111) & type) == 0)
				conversions.add(sm.getDeclaringClass().getName() + ": " + unit.toString());
			else {
				List<Unit> definitions = getDefsOfLocal(unit, (Local) v);
				if (!definitions.isEmpty()) {
					if (encryptions.containsKey(id))
						encryptions.put(id, (byte) (type | encryptions.get(id)));
					else
						encryptions.put(id, type);
				}
			}
		}
	}

	public List<Unit> getDefsOfLocal(Unit u, Local l) {
		List<Unit> defUnits = defs.getDefsOfAt(l, u);
		if (!defUnits.isEmpty()) {
			return defUnits;
		} else {
			assert (u instanceof AssignStmt);
			defUnits = new ArrayList<Unit>();
			defUnits.add(u);
			return defUnits;
		}
	}

}
