from pathlib import Path

root = Path('.')
history = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepHistoryPage.kt'
gradle = root / 'nextgen/androidApp/build.gradle.kts'

s = history.read_text()

s = s.replace(
'''    val totalMinutes = snapshot?.totalMinutes
    val deltaMinutes = if (totalMinutes != null && avgMinutes != null) totalMinutes - avgMinutes else null''',
'''    val totalMinutes = snapshot?.totalMinutes
    val sleepTimeMinutes = snapshot?.sleepTimeMinutes ?: totalMinutes?.let { it + (snapshot.awakeMinutes ?: 0) }
    val deltaMinutes = if (totalMinutes != null && avgMinutes != null) totalMinutes - avgMinutes else null'''
)

needle = '''                    Text(formatHistoryMinutes(totalMinutes), color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(5.dp))
                    Text(analysis?.headline ?: "Your night sky is still gathering data", color = Color.White.copy(alpha = .86f), fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)'''
replacement = '''                    Text(formatHistoryMinutes(totalMinutes), color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                    Text("Actual sleep", color = Color.White.copy(alpha = .60f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "Sleep time  ${formatHistoryMinutes(sleepTimeMinutes)}",
                                color = Color.White.copy(alpha = .92f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "Actual  ${formatHistoryMinutes(totalMinutes)}",
                                color = Color.White.copy(alpha = .92f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(analysis?.headline ?: "Your night sky is still gathering data", color = Color.White.copy(alpha = .86f), fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)'''

if needle not in s:
    raise SystemExit('Could not find interpreted dashboard hero block to patch')
s = s.replace(needle, replacement)

raw_needle = '''                    Text(formatHistoryMinutes(totalMinutes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("recorded sleep", color = Color.White.copy(alpha = .62f), fontSize = 8.sp)'''
raw_replacement = '''                    Text(formatHistoryMinutes(totalMinutes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("actual sleep", color = Color.White.copy(alpha = .62f), fontSize = 8.sp)
                    Text("Sleep time ${formatHistoryMinutes(sleepTimeMinutes)}", color = Color.White.copy(alpha = .82f), fontSize = 9.sp, fontWeight = FontWeight.Bold)'''
if raw_needle not in s:
    raise SystemExit('Could not find raw dashboard hero block to patch')
s = s.replace(raw_needle, raw_replacement)

history.write_text(s)

g = gradle.read_text()
g = g.replace('versionCode = 11204', 'versionCode = 11205')
g = g.replace('versionName = "11.2.4"', 'versionName = "11.2.5"')
gradle.write_text(g)
