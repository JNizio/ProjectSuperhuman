open ProjectSuperhuman.Science
open ProjectSuperhuman.Science.Engine

let assertTrue name cond = if not cond then failwithf "ScientificCore test failed: %s" name
let marker n v lo hi u = { Name=n; Value=v; Low=lo; High=hi; Unit=u; Timestamp=None }

let bili=marker "Bilirubin" 31.0 (Some 0.0) (Some 21.0) "µmol/L"
let alt=marker "ALT" 22.0 (Some 0.0) (Some 50.0) "U/L"
let albumin=marker "Albumin" 45.0 (Some 35.0) (Some 50.0) "g/L"
assertTrue "high classification" ((classify bili).Status=MarkerStatus.High)
assertTrue "normal classification" ((classify alt).Status=MarkerStatus.Normal)
let liver=aggregateSystem "Liver" [bili;alt;albumin]
assertTrue "system abnormal" (liver.AbnormalCount=1 && liver.NormalCount=2)
assertTrue "isolated bilirubin" (isolatedAbnormality "Bilirubin" [bili;alt;albumin])
let bp=bloodPressure 129 66 (Some 71)
assertTrue "bp plausible" bp.Plausible
assertTrue "pulse pressure" (bp.PulsePressure=63)
let t=trend [10.0;10.2;11.0]
assertTrue "trend rising" (t.Trend=Rising)
match pearson [1.;2.;3.;4.] [2.;4.;6.;8.] with | Some r -> assertTrue "pearson" (r > 0.999) | None -> failwith "pearson missing"
printfn "ScientificCore F# validation passed"
