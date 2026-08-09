pub fn mean(values: &[f64]) -> Option<f64> {
    if values.is_empty() { None } else { Some(values.iter().sum::<f64>() / values.len() as f64) }
}

pub fn rolling_mean(values: &[f64], window: usize) -> Vec<f64> {
    if window == 0 || values.len() < window { return vec![]; }
    values.windows(window).map(|w| w.iter().sum::<f64>() / window as f64).collect()
}

pub fn pearson(a: &[f64], b: &[f64]) -> Option<f64> {
    if a.len() != b.len() || a.len() < 3 { return None; }
    let ma=mean(a)?; let mb=mean(b)?;
    let mut num=0.0; let mut da=0.0; let mut db=0.0;
    for (x,y) in a.iter().zip(b.iter()) { let xa=*x-ma; let yb=*y-mb; num+=xa*yb; da+=xa*xa; db+=yb*yb; }
    let den=(da*db).sqrt(); if den == 0.0 { None } else { Some(num/den) }
}

pub fn median(mut values: Vec<f64>) -> Option<f64> {
    if values.is_empty() { return None; }
    values.sort_by(|a,b| a.total_cmp(b)); let n=values.len();
    Some(if n%2==1 { values[n/2] } else { (values[n/2-1]+values[n/2])/2.0 })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn stats_work(){
        assert_eq!(rolling_mean(&[1.,2.,3.,4.],2), vec![1.5,2.5,3.5]);
        assert!(pearson(&[1.,2.,3.,4.], &[2.,4.,6.,8.]).unwrap() > 0.999);
        assert_eq!(median(vec![3.,1.,2.]), Some(2.0));
    }
}
