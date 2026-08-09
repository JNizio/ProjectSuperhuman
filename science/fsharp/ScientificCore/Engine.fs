namespace ProjectSuperhuman.Science

open System

[<RequireQualifiedAccess>]
type MarkerStatus =
    | Low
    | Normal
    | High
    | Unknown

type Marker = {
    Name: string
    Value: float
    Low: float option
    High: float option
    Unit: string
    Timestamp: DateTimeOffset option
}

type MarkerInterpretation = {
    Name: string
    Status: MarkerStatus
    DeltaFromNearestLimit: float option
    Reason: string
}

type SystemSummary = {
    System: string
    Status: MarkerStatus
    NormalCount: int
    AbnormalCount: int
    UnknownCount: int
    Reasons: string list
}

type Trend =
    | Rising
    | Falling
    | Stable
    | Insufficient

type TrendSummary = {
    Trend: Trend
    Change: float option
    PercentChange: float option
    Points: int
}

type BloodPressureSummary = {
    Systolic: int
    Diastolic: int
    Pulse: int option
    PulsePressure: int
    MeanArterialPressure: float
    Plausible: bool
}

module Engine =
    let classify (m: Marker) =
        match m.Low, m.High with
        | Some lo, Some hi when m.Value < lo ->
            {
                Name = m.Name
                Status = MarkerStatus.Low
                DeltaFromNearestLimit = Some (m.Value - lo)
                Reason = sprintf "%s is below the supplied reference range (%g–%g %s)." m.Name lo hi m.Unit
            }
        | Some lo, Some hi when m.Value > hi ->
            {
                Name = m.Name
                Status = MarkerStatus.High
                DeltaFromNearestLimit = Some (m.Value - hi)
                Reason = sprintf "%s is above the supplied reference range (%g–%g %s)." m.Name lo hi m.Unit
            }
        | Some lo, Some hi ->
            {
                Name = m.Name
                Status = MarkerStatus.Normal
                DeltaFromNearestLimit = Some (Math.Min(m.Value - lo, hi - m.Value))
                Reason = sprintf "%s is within the supplied reference range." m.Name
            }
        | _ ->
            {
                Name = m.Name
                Status = MarkerStatus.Unknown
                DeltaFromNearestLimit = None
                Reason = sprintf "%s has no complete saved reference range." m.Name
            }

    let aggregateSystem name (markers: Marker list) =
        let xs = markers |> List.map classify
        let normalCount = xs |> List.filter (fun x -> x.Status = MarkerStatus.Normal) |> List.length
        let abnormalCount = xs |> List.filter (fun x -> x.Status = MarkerStatus.Low || x.Status = MarkerStatus.High) |> List.length
        let unknownCount = xs.Length - normalCount - abnormalCount
        let status =
            if abnormalCount > 0 then MarkerStatus.High
            elif normalCount > 0 && unknownCount = 0 then MarkerStatus.Normal
            else MarkerStatus.Unknown
        {
            System = name
            Status = status
            NormalCount = normalCount
            AbnormalCount = abnormalCount
            UnknownCount = unknownCount
            Reasons = xs |> List.map (fun x -> x.Reason)
        }

    let trend (values: float list) =
        match values with
        | [] | [_] ->
            { Trend = Insufficient; Change = None; PercentChange = None; Points = values.Length }
        | _ ->
            let first = List.head values
            let last = List.last values
            let change = last - first
            let pct = if abs first < 1e-9 then None else Some (change / first * 100.0)
            let tolerance = max 0.000001 (abs first * 0.01)
            let direction =
                if abs change <= tolerance then Stable
                elif change > 0.0 then Rising
                else Falling
            { Trend = direction; Change = Some change; PercentChange = pct; Points = values.Length }

    let bloodPressure systolic diastolic pulse =
        let pp = systolic - diastolic
        let mapValue = float diastolic + float pp / 3.0
        let pulseOk =
            match pulse with
            | None -> true
            | Some p -> p >= 30 && p <= 220
        {
            Systolic = systolic
            Diastolic = diastolic
            Pulse = pulse
            PulsePressure = pp
            MeanArterialPressure = mapValue
            Plausible = systolic >= 70 && systolic <= 280 && diastolic >= 35 && diastolic <= 180 && systolic > diastolic && pulseOk
        }

    let pearson (a: float list) (b: float list) =
        if a.Length <> b.Length || a.Length < 3 then None
        else
            let ma = List.average a
            let mb = List.average b
            let pairs = List.zip a b
            let num = pairs |> List.sumBy (fun (x, y) -> (x - ma) * (y - mb))
            let da = pairs |> List.sumBy (fun (x, _) -> pown (x - ma) 2)
            let db = pairs |> List.sumBy (fun (_, y) -> pown (y - mb) 2)
            let den = sqrt (da * db)
            if den = 0.0 then None else Some (num / den)

    let isolatedAbnormality targetName (panel: Marker list) =
        let xs = panel |> List.map classify
        let target = xs |> List.tryFind (fun x -> x.Name.Equals(targetName, StringComparison.OrdinalIgnoreCase))
        let others = xs |> List.filter (fun x -> not (x.Name.Equals(targetName, StringComparison.OrdinalIgnoreCase)))
        match target with
        | Some t when (t.Status = MarkerStatus.High || t.Status = MarkerStatus.Low) && (others |> List.forall (fun x -> x.Status = MarkerStatus.Normal || x.Status = MarkerStatus.Unknown)) -> true
        | _ -> false
