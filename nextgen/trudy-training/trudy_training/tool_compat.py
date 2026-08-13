from __future__ import annotations
INTELLIGENCE_TOOLS={"get_personal_trend","compare_baseline","get_association","get_lagged_association","generate_experiment_hypothesis","evaluate_experiment"}
RUNTIME_TOOLS={"get_domain_state","get_metric_history","get_domain_history","get_derived_features","get_insights","get_data_quality","get_context"}|INTELLIGENCE_TOOLS

def validate_tool_operation(op:dict)->list[str]:
    errors=[]; name=op.get("name"); domains=op.get("domains",[]); args=op.get("arguments",{})
    if name not in RUNTIME_TOOLS: errors.append(f"unsupported tool operation: {name}")
    if not isinstance(domains,list): errors.append("domains must be a list")
    if not isinstance(args,dict): errors.append("arguments must be an object")
    if name in {"get_association","get_lagged_association"} and len(domains)!=2: errors.append("association tools require exactly two explicit domains")
    if name in INTELLIGENCE_TOOLS-{"get_association","get_lagged_association"} and not domains: errors.append("intelligence tool requires explicit domain")
    return errors
