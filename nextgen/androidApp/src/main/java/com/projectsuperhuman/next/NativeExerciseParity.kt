package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private val ExerciseNavy=Color(0xFF082D66)
private val ExerciseBlue=Color(0xFF0D6CB4)
private val ExerciseInk=Color(0xFF0B1F35)
private val ExerciseMuted=Color(0xFF64748B)
private val ExerciseOrange=Color(0xFFD97706)
private val ExerciseGreen=Color(0xFF168A78)
private val ExerciseBg=Color(0xFFF5F8FC)
private val ExerciseSoft=Color(0xFFEAF4FF)
private val ExercisePurple=Color(0xFF6559C7)
private val ExerciseSky=Color(0xFF77C8FF)

data class NativeExercise(val id:String,val name:String,val group:String,val equipment:String,val difficulty:String="",val mechanic:String="",val met:Double=0.0,val primaryMuscles:List<String> = emptyList(),val secondaryMuscles:List<String> = emptyList(),val instructions:List<String> = emptyList(),val tips:List<String> = emptyList(),val imageStart:String?=null,val imagePeak:String?=null,val imageMain:String?=null)
data class NativeWorkoutSet(val exercise:NativeExercise,val reps:Int,val loadKg:Double,val timestamp:Long,val type:String="Work",val rir:Int?=null,val rpe:Double?=null,val supersetTag:String?=null){val volume get()=reps*loadKg}
data class WorkoutRoutine(val name:String,val exerciseIds:List<String>)

private fun jsonStrings(a:JSONArray?):List<String> = if(a==null) emptyList() else (0 until a.length()).mapNotNull{a.optString(it).takeIf(String::isNotBlank)}
private fun pretty(s:String)=s.replace('_',' ').replaceFirstChar{it.uppercase()}
private fun exerciseNumber(v:Double)=if(v%1.0==0.0)v.toInt().toString() else String.format(java.util.Locale.US,"%.1f",v)
private suspend fun loadRepDb(context:android.content.Context)=withContext(Dispatchers.IO){runCatching{val root=JSONObject(context.assets.open("repdb/exercises.json").bufferedReader().use{it.readText()});val a=root.getJSONArray("exercises");(0 until a.length()).map{i->val o=a.getJSONObject(i);val flat=o.optJSONObject("images")?.optJSONObject("flat");NativeExercise(o.getString("id"),o.optString("name_en",o.getString("id")),pretty(o.optString("body_part","Other")),pretty(o.optString("equipment","Bodyweight")),pretty(o.optString("difficulty")),pretty(o.optString("mechanic")),o.optDouble("met",0.0),jsonStrings(o.optJSONArray("primary_muscles")),jsonStrings(o.optJSONArray("secondary_muscles")),jsonStrings(o.optJSONArray("instructions_en")),jsonStrings(o.optJSONArray("tips_en")),flat?.optString("start")?.takeIf(String::isNotBlank),flat?.optString("peak")?.takeIf(String::isNotBlank),flat?.optString("main")?.takeIf(String::isNotBlank))}}.getOrElse{emptyList<NativeExercise>()}}
private fun loadRoutines(context:android.content.Context):List<WorkoutRoutine>{val raw=context.getSharedPreferences("superhuman_training",0).getString("routines","[]")?:"[]";return runCatching{val a=JSONArray(raw);(0 until a.length()).map{i->val o=a.getJSONObject(i);WorkoutRoutine(o.getString("name"),jsonStrings(o.optJSONArray("exerciseIds")))}}.getOrElse{emptyList()}}
private fun saveRoutines(context:android.content.Context,routines:List<WorkoutRoutine>){val a=JSONArray();routines.forEach{r->a.put(JSONObject().put("name",r.name).put("exerciseIds",JSONArray(r.exerciseIds)))};context.getSharedPreferences("superhuman_training",0).edit().putString("routines",a.toString()).apply()}

@Composable private fun RepDbImage(path:String?,modifier:Modifier){
 val context=LocalContext.current
 val bitmap by produceState<android.graphics.Bitmap?>(null,path){value=withContext(Dispatchers.IO){path?.let{runCatching{context.assets.open("repdb/$it").use(BitmapFactory::decodeStream)}.getOrNull()}}}
 Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFF7FBFF),Color(0xFFE9F4FC))),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit) else Text("EX",color=ExerciseBlue,fontWeight=FontWeight.Black)}
}

@Composable internal fun NativeExerciseParityScreen(onBack:()->Unit,openLegacy:()->Unit){
 val context=LocalContext.current; val scope=rememberCoroutineScope()
 var catalog by remember{mutableStateOf<List<NativeExercise>>(emptyList())}; var recent by remember{mutableStateOf<List<HealthValue>>(emptyList())}; var mode by remember{mutableStateOf("home")}; var selected by remember{mutableStateOf<NativeExercise?>(null)}; var query by remember{mutableStateOf("")}; var showCount by remember{mutableStateOf(12)}
 val session=remember{mutableStateListOf<NativeWorkoutSet>()}; val workoutExercises=remember{mutableStateListOf<NativeExercise>()}; var repsText by remember{mutableStateOf("10")}; var loadText by remember{mutableStateOf("0")}; var rirText by remember{mutableStateOf("2")}; var rpeText by remember{mutableStateOf("")}; var setType by remember{mutableStateOf("Work")}; var supersetTag by remember{mutableStateOf<String?>(null)}; var status by remember{mutableStateOf("")}; var startedAt by remember{mutableLongStateOf(0L)}
 var restSeconds by remember{mutableIntStateOf(0)}; var restTarget by remember{mutableIntStateOf(120)}; var routines by remember{mutableStateOf(loadRoutines(context))}; var routineName by remember{mutableStateOf("")}; var routineSelection by remember{mutableStateOf<List<String>>(emptyList())}; var summarySets by remember{mutableIntStateOf(0)}; var summaryVolume by remember{mutableDoubleStateOf(0.0)}; var summaryDuration by remember{mutableIntStateOf(0)}
 suspend fun refresh(){val n=System.currentTimeMillis();recent=NativeDataHub.between("exercise_set",n-365L*86400000L,n).sortedByDescending{it.timestampEpochMs}.take(400)}
 LaunchedEffect(Unit){catalog=loadRepDb(context);refresh()}
 LaunchedEffect(restSeconds){if(restSeconds>0){delay(1000);restSeconds-=1}}
 fun startWorkout(exercises:List<NativeExercise> = emptyList()){session.clear();workoutExercises.clear();workoutExercises.addAll(exercises);selected=exercises.firstOrNull();startedAt=System.currentTimeMillis();mode="workout";status="Workout started"}
 fun finishWorkout(){val now=System.currentTimeMillis();summarySets=session.size;summaryVolume=session.sumOf{it.volume};summaryDuration=max(1,((now-startedAt)/60000L).toInt());scope.launch{NativeDataHub.saveValues(listOf(HealthValue(HealthDomain.EXERCISE,"workout_session",summarySets.toDouble(),"sets",now,"repdb-exercise",mapOf("volumeKg" to summaryVolume.toString(),"durationMin" to summaryDuration.toString(),"exerciseCount" to workoutExercises.distinctBy{it.id}.size.toString()))));refresh();mode="summary"};restSeconds=0}
 val weekRecent=recent.filter{it.timestampEpochMs>System.currentTimeMillis()-7L*86400000L}

 Column(Modifier.fillMaxSize().background(ExerciseBg).verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  TrainingHeader(mode,onBack={if(mode=="home")onBack() else mode="home"})
  when(mode){
   "home"->{
    val last=recent.firstOrNull(); val lastName=last?.metadata?.get("exerciseName")?:"No workout logged yet"
    TrainingHero(lastName,weekRecent.size,weekRecent.sumOf{it.value}.roundToInt()){startWorkout()}
    Row(horizontalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.fillMaxWidth()){
     TrainingNavTile("R","ROUTINES","Saved plans",ExerciseBlue,Modifier.weight(1f)){mode="routines"}
     TrainingNavTile("H","HISTORY","Past sessions",ExercisePurple,Modifier.weight(1f)){mode="history"}
     TrainingNavTile("P","PROGRESS","Trends & PRs",ExerciseGreen,Modifier.weight(1f)){mode="progress"}
    }
    if(routines.isNotEmpty()) PolishedSection("QUICK ROUTINES","Jump straight into a saved plan") {routines.take(3).forEach{r->RoutineRow(r,catalog){startWorkout(it)}}}
    PolishedSection("TRAINING OVERVIEW","Your last 7 days"){
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
      MetricTile("SETS",weekRecent.size.toString(),"completed",ExerciseBlue,Modifier.weight(1f))
      MetricTile("VOLUME","${weekRecent.sumOf{it.value}.roundToInt()}","kg moved",ExerciseGreen,Modifier.weight(1f))
      MetricTile("LIBRARY",catalog.size.toString(),"exercises",ExercisePurple,Modifier.weight(1f))
     }
     Spacer(Modifier.height(12.dp));WideActionTile("EXERCISE LIBRARY","Browse illustrated movements, form and equipment",ExerciseBlue){mode="library"}
    }
   }
   "library"->{
    val filtered=remember(catalog,query){val q=query.trim().lowercase();if(q.isBlank())catalog else catalog.filter{e->listOf(e.name,e.group,e.equipment,e.difficulty,e.mechanic).any{it.lowercase().contains(q)}||e.primaryMuscles.any{it.contains(q,true)}}}
    HeroStrip("EXERCISE LIBRARY","${catalog.size} illustrated movements","Search muscles, equipment and movement patterns",ExerciseBlue)
    PolishedSection("FIND AN EXERCISE","Fast local search"){
     OutlinedTextField(query,{query=it;showCount=12},Modifier.fillMaxWidth(),singleLine=true,label={Text("Search exercises")});Spacer(Modifier.height(8.dp))
     filtered.take(showCount).forEach{e->ExerciseResultRow(e,startedAt>0L,{selected=e;mode="detail"},{if(workoutExercises.none{it.id==e.id})workoutExercises.add(e);selected=e;mode="workout"})}
     if(filtered.size>showCount)Text("LOAD 12 MORE",color=ExerciseBlue,fontSize=10.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().clickable{showCount+=12}.padding(12.dp))
    }
   }
   "detail"->{selected?.let{e->
    HeroStrip("MOVEMENT PROFILE",e.name,"${e.group} · ${e.equipment}",ExercisePurple)
    PolishedSection("FORM & EXECUTION","${e.difficulty} · ${e.mechanic}${if(e.met>0)" · ${exerciseNumber(e.met)} MET" else ""}"){
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){RepDbImage(e.imageMain?:e.imageStart,Modifier.weight(1f).height(160.dp));if(e.imagePeak!=null)RepDbImage(e.imagePeak,Modifier.weight(1f).height(160.dp))}
     if(e.primaryMuscles.isNotEmpty()){Spacer(Modifier.height(10.dp));ChipRow(e.primaryMuscles.take(4).map{pretty(it)})}
    }
    if(e.instructions.isNotEmpty()) PolishedSection("HOW TO","Movement sequence"){e.instructions.take(6).forEachIndexed{i,s->InstructionRow(i+1,s)}}
    if(e.tips.isNotEmpty()) PolishedSection("FORM TIPS","Keep the movement clean"){e.tips.take(3).forEach{TipRow(it)}}
    WideActionTile(if(startedAt==0L)"START WITH THIS EXERCISE" else "ADD TO WORKOUT",if(startedAt==0L)"Begin a new session" else "Add it to the live session",ExerciseNavy){if(startedAt==0L)startWorkout(listOf(e)) else {if(workoutExercises.none{it.id==e.id})workoutExercises.add(e);selected=e;mode="workout"}}
   }}
   "routines"->{
    HeroStrip("ROUTINES","Your repeatable training plans","Build once, start instantly",ExerciseBlue)
    PolishedSection("SAVED ROUTINES","Tap a routine to start"){
     if(routines.isEmpty())EmptyState("No routines yet","Create your first routine below.")
     routines.forEachIndexed{i,r->Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).background(ExerciseSoft,CircleShape),contentAlignment=Alignment.Center){Text("${r.exerciseIds.size}",color=ExerciseBlue,fontWeight=FontWeight.Black)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f).clickable{startWorkout(r.exerciseIds.mapNotNull{id->catalog.find{it.id==id}})}){Text(r.name,color=ExerciseNavy,fontSize=13.sp,fontWeight=FontWeight.Black);Text("${r.exerciseIds.size} exercises",color=ExerciseMuted,fontSize=8.sp)};Text("Remove",color=Color(0xFFAA6666),fontSize=9.sp,modifier=Modifier.clickable{routines=routines.filterIndexed{idx,_->idx!=i};saveRoutines(context,routines)})}
    }
    PolishedSection("CREATE ROUTINE","Choose a name and exercises"){
     OutlinedTextField(routineName,{routineName=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Routine name")});Spacer(Modifier.height(8.dp));catalog.take(20).forEach{e->val picked=routineSelection.contains(e.id);Row(Modifier.fillMaxWidth().clickable{routineSelection=if(picked)routineSelection-e.id else routineSelection+e.id}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(25.dp).background(if(picked)ExerciseGreen else Color(0xFFF0F4F7),CircleShape),contentAlignment=Alignment.Center){Text(if(picked)"✓" else "",color=Color.White,fontSize=10.sp)};Spacer(Modifier.width(9.dp));Text(e.name,color=ExerciseInk,fontSize=10.sp)}}
     Spacer(Modifier.height(10.dp));WideActionTile("SAVE ROUTINE","${routineSelection.size} exercises selected",ExerciseNavy){if(routineName.isNotBlank()&&routineSelection.isNotEmpty()){routines=routines+WorkoutRoutine(routineName.trim(),routineSelection);saveRoutines(context,routines);routineName="";routineSelection=emptyList()}}
    }
   }
   "workout"->{
    LiveWorkoutHero(session.size,workoutExercises.distinctBy{it.id}.size,startedAt){finishWorkout()}
    if(restSeconds>0) RestTimerTile(restSeconds,{restSeconds=max(0,restSeconds-15)},{restSeconds+=15},{restSeconds=0})
    if(workoutExercises.isEmpty()) PolishedSection("BUILD YOUR SESSION","Choose your first movement"){EmptyState("No exercises yet","Open the library and add a movement.");Spacer(Modifier.height(8.dp));WideActionTile("ADD EXERCISE","Browse the RepDB library",ExerciseBlue){mode="library"}}
    else {
     PolishedSection("EXERCISES","Switch movements instantly"){
      Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){workoutExercises.take(4).forEach{e->Text(e.name.take(12),color=if(selected?.id==e.id)Color.White else ExerciseBlue,fontSize=8.sp,maxLines=1,modifier=Modifier.weight(1f).background(if(selected?.id==e.id)ExerciseBlue else ExerciseSoft,RoundedCornerShape(12.dp)).clickable{selected=e}.padding(9.dp))}}
     }
     selected?.let{e->
      PolishedSection("ACTIVE EXERCISE",e.name){
       Row(verticalAlignment=Alignment.CenterVertically){RepDbImage(e.imageMain?:e.imageStart,Modifier.size(72.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(e.name,color=ExerciseInk,fontSize=15.sp,fontWeight=FontWeight.Black);Text("${e.group} · ${e.equipment}",color=ExerciseMuted,fontSize=8.sp)};StatusPill(if(supersetTag==null)"SUPERSET" else "LINKED",supersetTag!=null){supersetTag=if(supersetTag==null)"A" else null}}
       Spacer(Modifier.height(12.dp));SetTableHeader();val old=recent.filter{it.metadata["exerciseId"]==e.id};session.filter{it.exercise.id==e.id}.forEachIndexed{i,s->SetRow(i+1,s,old.getOrNull(i))}
      }
      PolishedSection("LOG NEXT SET","Fast entry with effort context"){
       Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Warmup","Work","Drop","Failure").forEach{t->ChoiceChip(t,setType==t){setType=t}}}
       Spacer(Modifier.height(9.dp));Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(loadText,{loadText=it.filter{c->c.isDigit()||c=='.'}.take(6)},Modifier.weight(1f),singleLine=true,label={Text("kg")});OutlinedTextField(repsText,{repsText=it.filter(Char::isDigit).take(3)},Modifier.weight(1f),singleLine=true,label={Text("reps")});OutlinedTextField(rirText,{rirText=it.filter(Char::isDigit).take(1)},Modifier.weight(.8f),singleLine=true,label={Text("RIR")})}
       Spacer(Modifier.height(7.dp));Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(rpeText,{rpeText=it.filter{c->c.isDigit()||c=='.'}.take(4)},Modifier.weight(1f),singleLine=true,label={Text("RPE optional")});OutlinedTextField(restTarget.toString(),{it.toIntOrNull()?.let{v->restTarget=v.coerceIn(15,600)}},Modifier.weight(1f),singleLine=true,label={Text("Rest sec")})}
       Spacer(Modifier.height(10.dp));WideActionTile("✓ COMPLETE SET","Start ${restTarget}s rest timer",ExerciseGreen){val r=repsText.toIntOrNull()?:0;val l=loadText.toDoubleOrNull()?:0.0;if(r>0){val set=NativeWorkoutSet(e,r,l,System.currentTimeMillis(),setType,rirText.toIntOrNull(),rpeText.toDoubleOrNull(),supersetTag);session.add(set);restSeconds=restTarget;scope.launch{NativeDataHub.saveValues(listOf(HealthValue(HealthDomain.EXERCISE,"exercise_set",set.volume,"kg-reps",set.timestamp,"repdb-exercise",mapOf("exerciseId" to e.id,"exerciseName" to e.name,"group" to e.group,"equipment" to e.equipment,"reps" to r.toString(),"loadKg" to l.toString(),"met" to e.met.toString(),"setType" to setType,"rir" to (set.rir?.toString()?:""),"rpe" to (set.rpe?.toString()?:""),"superset" to (set.supersetTag?:"")) )));refresh()}}}
      }
      WideActionTile("+ ADD EXERCISE","Keep building this session",ExerciseBlue){mode="library"}
     }
    }
   }
   "history"->{
    HeroStrip("HISTORY","Your training timeline","Recent sets and movement history",ExercisePurple)
    PolishedSection("RECENT TRAINING","Latest logged sets"){
     if(recent.isEmpty())EmptyState("No training history yet","Your completed sets will appear here.") else recent.take(40).forEach{v->HistoryRow(v)}
    }
   }
   "progress"->{
    val grouped=recent.groupBy{it.metadata["exerciseName"]?:"Exercise"}.entries.sortedByDescending{it.value.size}.take(6)
    HeroStrip("PROGRESS","Performance trends","Volume, frequency and best loads",ExerciseGreen)
    PolishedSection("TOP MOVEMENTS","Most trained exercises"){
     if(grouped.isEmpty())EmptyState("No progress data yet","Repeat exercises over time to unlock trends.")
     grouped.forEach{(name,values)->val maxLoad=values.mapNotNull{it.metadata["loadKg"]?.toDoubleOrNull()}.maxOrNull()?:0.0;ProgressTile(name,values.size,maxLoad,values.take(12).reversed().map{it.value.toFloat()})}
    }
   }
   "summary"->{
    val bestPrevious=recent.take(100).sumOf{it.value};val insight=when{summarySets==0->"No completed sets were recorded.";summaryVolume>bestPrevious/10.0&&summarySets>=8->"Strong training output. Volume was high relative to your recent logged baseline.";summarySets>=12->"Solid training density. Recovery, sleep and nutrition can now be compared against this session.";else->"Session captured. More repeated workouts will sharpen progression and recovery insights."}
    SummaryHero(summarySets,summaryVolume,summaryDuration)
    PolishedSection("SUPERHUMAN INSIGHT","Training-context interpretation"){Row(verticalAlignment=Alignment.Top){Box(Modifier.size(34.dp).background(Color(0xFFE6F6F1),CircleShape),contentAlignment=Alignment.Center){Text("S",color=ExerciseGreen,fontWeight=FontWeight.Black)};Spacer(Modifier.width(10.dp));Text(insight,color=ExerciseInk,fontSize=11.sp,lineHeight=17.sp,modifier=Modifier.weight(1f))};Spacer(Modifier.height(10.dp));Text("This is a training-context observation, not a medical conclusion.",color=ExerciseMuted,fontSize=8.sp)}
    WideActionTile("DONE","Return to training dashboard",ExerciseNavy){session.clear();workoutExercises.clear();startedAt=0L;mode="home"}
   }
  }
  Text("Exercise data & illustrations by RepDB · repdb.co",color=ExerciseMuted,fontSize=8.sp,modifier=Modifier.padding(6.dp));Spacer(Modifier.height(18.dp))
 }
}

@Composable private fun TrainingHeader(mode:String,onBack:()->Unit){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.superhumanTopButton(onClick=onBack),contentAlignment=Alignment.Center){Text("←",color=ExerciseBlue,fontSize=28.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(12.dp));Column{Text(when(mode){"workout"->"Live workout";"library"->"Exercises";"routines"->"Routines";"history"->"History";"progress"->"Progress";"summary"->"Workout complete";else->"Training"},color=ExerciseInk,fontSize=25.sp,fontWeight=FontWeight.Black);Text(if(mode=="workout")"Focused session mode" else "Train · track · progress",color=ExerciseMuted,fontSize=10.sp)}}}

@Composable private fun TrainingHero(lastName:String,sets:Int,volume:Int,onStart:()->Unit){Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0A3168),Color(0xFF0D6CB4),Color(0xFF5BA9DB))),RoundedCornerShape(28.dp)).padding(21.dp)){Canvas(Modifier.matchParentSize()){drawCircle(Color.White.copy(alpha=.08f),size.width*.22f,Offset(size.width*.88f,size.height*.08f));drawCircle(Color.White.copy(alpha=.06f),size.width*.15f,Offset(size.width*.72f,size.height*.82f))};Column{Text("TRAINING TODAY",color=Color.White.copy(alpha=.72f),fontSize=9.sp,fontWeight=FontWeight.Black,letterSpacing=1.2.sp);Spacer(Modifier.height(6.dp));Text("Ready to train?",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Black);Text("Last activity · $lastName",color=Color.White.copy(alpha=.72f),fontSize=10.sp);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){GlassMetric("7D SETS",sets.toString(),Modifier.weight(1f));GlassMetric("7D VOLUME","$volume kg",Modifier.weight(1f))};Spacer(Modifier.height(15.dp));Box(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(16.dp)).clickable{onStart()}.padding(15.dp),contentAlignment=Alignment.Center){Text("START EMPTY WORKOUT",color=ExerciseNavy,fontSize=12.sp,fontWeight=FontWeight.Black)}}}}
@Composable private fun GlassMetric(label:String,value:String,modifier:Modifier){Column(modifier.background(Color.White.copy(alpha=.13f),RoundedCornerShape(14.dp)).padding(10.dp)){Text(label,color=Color.White.copy(alpha=.62f),fontSize=7.sp,fontWeight=FontWeight.Bold);Text(value,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Black)}}
@Composable private fun TrainingNavTile(mark:String,title:String,subtitle:String,accent:Color,modifier:Modifier,onClick:()->Unit){Column(modifier.background(Color.White,RoundedCornerShape(20.dp)).clickable{onClick()}.padding(12.dp)){Box(Modifier.size(32.dp).background(accent.copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center){Text(mark,color=accent,fontWeight=FontWeight.Black)};Spacer(Modifier.height(9.dp));Text(title,color=ExerciseNavy,fontSize=9.sp,fontWeight=FontWeight.Black);Text(subtitle,color=ExerciseMuted,fontSize=7.sp)}}
@Composable private fun PolishedSection(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(23.dp)).border(1.dp,Color(0xFFE9EFF5),RoundedCornerShape(23.dp)).padding(16.dp)){Text(title,color=ExerciseInk,fontSize=16.sp,fontWeight=FontWeight.Black);Text(subtitle,color=ExerciseMuted,fontSize=8.sp);Spacer(Modifier.height(11.dp));content()}}
@Composable private fun MetricTile(label:String,value:String,detail:String,accent:Color,modifier:Modifier){Column(modifier.background(accent.copy(alpha=.07f),RoundedCornerShape(16.dp)).padding(11.dp)){Box(Modifier.size(5.dp).background(accent,CircleShape));Spacer(Modifier.height(6.dp));Text(label,color=ExerciseMuted,fontSize=7.sp,fontWeight=FontWeight.Bold);Text(value,color=ExerciseNavy,fontSize=15.sp,fontWeight=FontWeight.Black);Text(detail,color=ExerciseMuted,fontSize=7.sp)}}
@Composable private fun WideActionTile(title:String,subtitle:String,accent:Color,onClick:()->Unit){Row(Modifier.fillMaxWidth().background(accent.copy(alpha=.09f),RoundedCornerShape(17.dp)).clickable{onClick()}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(accent,CircleShape));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,color=if(accent==ExerciseNavy)ExerciseNavy else accent,fontSize=10.sp,fontWeight=FontWeight.Black);Text(subtitle,color=ExerciseMuted,fontSize=8.sp)};Text("→",color=accent,fontSize=18.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun HeroStrip(kicker:String,title:String,subtitle:String,accent:Color){Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha=.96f),accent.copy(alpha=.72f))),RoundedCornerShape(24.dp)).padding(18.dp)){Text(kicker,color=Color.White.copy(alpha=.68f),fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp);Spacer(Modifier.height(5.dp));Text(title,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Black);Text(subtitle,color=Color.White.copy(alpha=.76f),fontSize=9.sp)}}
@Composable private fun RoutineRow(r:WorkoutRoutine,catalog:List<NativeExercise>,start:(List<NativeExercise>)->Unit){Row(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC),RoundedCornerShape(15.dp)).clickable{start(r.exerciseIds.mapNotNull{id->catalog.find{it.id==id}})}.padding(11.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(34.dp).background(ExerciseSoft,CircleShape),contentAlignment=Alignment.Center){Text(r.exerciseIds.size.toString(),color=ExerciseBlue,fontWeight=FontWeight.Black)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(r.name,color=ExerciseNavy,fontSize=11.sp,fontWeight=FontWeight.Black);Text("${r.exerciseIds.size} exercises",color=ExerciseMuted,fontSize=8.sp)};Text("→",color=ExerciseBlue)}}
@Composable private fun ExerciseResultRow(e:NativeExercise,inWorkout:Boolean,onOpen:()->Unit,onAdd:()->Unit){Row(Modifier.fillMaxWidth().background(Color(0xFFF9FBFD),RoundedCornerShape(17.dp)).clickable{onOpen()}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){RepDbImage(e.imageMain?:e.imageStart,Modifier.size(62.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(e.name,color=ExerciseInk,fontSize=12.sp,fontWeight=FontWeight.ExtraBold);Text("${e.group} · ${e.equipment}",color=ExerciseMuted,fontSize=8.sp);if(e.primaryMuscles.isNotEmpty())Text(e.primaryMuscles.take(2).joinToString(" · "){pretty(it)},color=ExerciseBlue,fontSize=8.sp)};if(inWorkout)Box(Modifier.size(30.dp).background(ExerciseSoft,CircleShape).clickable{onAdd()},contentAlignment=Alignment.Center){Text("+",color=ExerciseBlue,fontWeight=FontWeight.Black,fontSize=18.sp)}};Spacer(Modifier.height(6.dp))}
@Composable private fun ChipRow(items:List<String>){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){items.forEach{Text(it,color=ExerciseBlue,fontSize=7.sp,fontWeight=FontWeight.Bold,modifier=Modifier.background(ExerciseSoft,RoundedCornerShape(10.dp)).padding(horizontal=8.dp,vertical=5.dp))}}}
@Composable private fun InstructionRow(n:Int,text:String){Row(Modifier.padding(vertical=5.dp),verticalAlignment=Alignment.Top){Box(Modifier.size(24.dp).background(ExerciseSoft,CircleShape),contentAlignment=Alignment.Center){Text(n.toString(),color=ExerciseBlue,fontSize=8.sp,fontWeight=FontWeight.Black)};Spacer(Modifier.width(9.dp));Text(text,color=ExerciseInk,fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.weight(1f))}}
@Composable private fun TipRow(text:String){Row(Modifier.padding(vertical=5.dp),verticalAlignment=Alignment.Top){Box(Modifier.size(6.dp).background(ExerciseGreen,CircleShape).padding(top=4.dp));Spacer(Modifier.width(9.dp));Text(text,color=ExerciseMuted,fontSize=9.sp,lineHeight=14.sp)}}
@Composable private fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC),RoundedCornerShape(16.dp)).padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=ExerciseInk,fontSize=11.sp,fontWeight=FontWeight.Black);Text(subtitle,color=ExerciseMuted,fontSize=8.sp,textAlign=TextAlign.Center)}}
@Composable private fun LiveWorkoutHero(sets:Int,exercises:Int,startedAt:Long,onFinish:()->Unit){val mins=if(startedAt>0)((System.currentTimeMillis()-startedAt)/60000L).coerceAtLeast(0) else 0;Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF092D63),Color(0xFF154F8F))),RoundedCornerShape(24.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("LIVE SESSION",color=Color.White.copy(alpha=.65f),fontSize=8.sp,fontWeight=FontWeight.Black);Text("$sets sets · $exercises exercises",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Black);Text("${mins}m elapsed",color=Color.White.copy(alpha=.65f),fontSize=8.sp)};Box(Modifier.background(Color.White,RoundedCornerShape(13.dp)).clickable{onFinish()}.padding(horizontal=14.dp,vertical=10.dp)){Text("FINISH",color=ExerciseNavy,fontSize=9.sp,fontWeight=FontWeight.Black)}}}
@Composable private fun RestTimerTile(seconds:Int,minus:()->Unit,plus:()->Unit,skip:()->Unit){Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0E7C70),Color(0xFF2EA995))),RoundedCornerShape(19.dp)).padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(34.dp).background(Color.White.copy(alpha=.15f),CircleShape),contentAlignment=Alignment.Center){Text("R",color=Color.White,fontWeight=FontWeight.Black)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("REST TIMER",color=Color.White.copy(alpha=.65f),fontSize=7.sp,fontWeight=FontWeight.Bold);Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Black)};Text("−15",color=Color.White,fontSize=9.sp,modifier=Modifier.clickable{minus()}.padding(7.dp));Text("+15",color=Color.White,fontSize=9.sp,modifier=Modifier.clickable{plus()}.padding(7.dp));Text("SKIP",color=Color.White,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{skip()}.padding(7.dp))}}
@Composable private fun StatusPill(label:String,active:Boolean,onClick:()->Unit){Text(label,color=if(active)Color.White else ExerciseBlue,fontSize=7.sp,fontWeight=FontWeight.Black,modifier=Modifier.background(if(active)ExerciseGreen else ExerciseSoft,RoundedCornerShape(12.dp)).clickable{onClick()}.padding(horizontal=8.dp,vertical=6.dp))}
@Composable private fun SetTableHeader(){Row{Text("SET",Modifier.width(34.dp),color=ExerciseMuted,fontSize=7.sp);Text("PREVIOUS",Modifier.weight(1f),color=ExerciseMuted,fontSize=7.sp);Text("KG",Modifier.width(54.dp),color=ExerciseMuted,fontSize=7.sp);Text("REPS",Modifier.width(48.dp),color=ExerciseMuted,fontSize=7.sp);Text("RIR",Modifier.width(38.dp),color=ExerciseMuted,fontSize=7.sp)}}
@Composable private fun SetRow(i:Int,s:NativeWorkoutSet,old:HealthValue?){Row(Modifier.fillMaxWidth().background(if(i%2==0)Color(0xFFF9FBFD) else Color.Transparent,RoundedCornerShape(10.dp)).padding(vertical=7.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){Text(i.toString(),Modifier.width(30.dp),color=ExerciseInk,fontSize=9.sp,fontWeight=FontWeight.Bold);Text(old?.let{"${it.metadata["loadKg"]?:"0"} × ${it.metadata["reps"]?:"—"}"}?:"—",Modifier.weight(1f),color=ExerciseMuted,fontSize=8.sp);Text(exerciseNumber(s.loadKg),Modifier.width(54.dp),color=ExerciseInk,fontSize=9.sp);Text(s.reps.toString(),Modifier.width(48.dp),color=ExerciseInk,fontSize=9.sp);Text(s.rir?.toString()?:"—",Modifier.width(38.dp),color=ExerciseInk,fontSize=9.sp)}}
@Composable private fun ChoiceChip(label:String,active:Boolean,onClick:()->Unit){Text(label,color=if(active)Color.White else ExerciseBlue,fontSize=8.sp,fontWeight=FontWeight.Bold,modifier=Modifier.background(if(active)ExerciseBlue else ExerciseSoft,RoundedCornerShape(11.dp)).clickable{onClick()}.padding(horizontal=9.dp,vertical=7.dp))}
@Composable private fun HistoryRow(v:HealthValue){Row(Modifier.fillMaxWidth().background(Color(0xFFF9FBFD),RoundedCornerShape(14.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(32.dp).background(ExerciseSoft,CircleShape),contentAlignment=Alignment.Center){Text("✓",color=ExerciseBlue,fontWeight=FontWeight.Black)};Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(v.metadata["exerciseName"]?:"Exercise",color=ExerciseInk,fontSize=11.sp,fontWeight=FontWeight.Bold);Text("${v.metadata["setType"]?:"Work"} · RIR ${v.metadata["rir"]?.ifBlank{"—"}?:"—"}",color=ExerciseMuted,fontSize=8.sp)};Text("${v.metadata["loadKg"]?:"0"} kg × ${v.metadata["reps"]?:"—"}",color=ExerciseNavy,fontSize=9.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(5.dp))}
@Composable private fun ProgressTile(name:String,sets:Int,maxLoad:Double,values:List<Float>){Column(Modifier.fillMaxWidth().background(Color(0xFFF9FBFD),RoundedCornerShape(16.dp)).padding(11.dp)){Row{Column(Modifier.weight(1f)){Text(name,color=ExerciseInk,fontSize=11.sp,fontWeight=FontWeight.Black);Text("$sets sets",color=ExerciseMuted,fontSize=8.sp)};Text("${exerciseNumber(maxLoad)} kg",color=ExerciseGreen,fontSize=11.sp,fontWeight=FontWeight.Black)};MiniVolumeChart(values)};Spacer(Modifier.height(7.dp))}
@Composable private fun SummaryHero(sets:Int,volume:Double,duration:Int){Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF103A70),Color(0xFF166E9F),Color(0xFF1D9B85))),RoundedCornerShape(28.dp)).padding(21.dp)){Text("WORKOUT COMPLETE",color=Color.White.copy(alpha=.66f),fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=1.1.sp);Spacer(Modifier.height(5.dp));Text("Session captured",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(13.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){GlassMetric("SETS",sets.toString(),Modifier.weight(1f));GlassMetric("VOLUME","${volume.roundToInt()} kg",Modifier.weight(1f));GlassMetric("TIME","${duration}m",Modifier.weight(1f))}}}
@Composable private fun MiniVolumeChart(values:List<Float>){val maxV=(values.maxOrNull()?:1f).coerceAtLeast(1f);Canvas(Modifier.fillMaxWidth().height(48.dp).padding(top=7.dp)){if(values.size<2)return@Canvas;val step=size.width/(values.size-1);values.zipWithNext().forEachIndexed{i,(a,b)->drawLine(ExerciseBlue,Offset(step*i,size.height-(a/maxV)*size.height),Offset(step*(i+1),size.height-(b/maxV)*size.height),strokeWidth=4f)}}}
