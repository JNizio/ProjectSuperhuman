package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

private val ExerciseNavy = Color(0xFF082D66)
private val ExerciseBlue = Color(0xFF0D6CB4)
private val ExerciseInk = Color(0xFF0B1F35)
private val ExerciseMuted = Color(0xFF64748B)
private val ExerciseOrange = Color(0xFFD97706)
private val ExerciseGreen = Color(0xFF168A78)
private val ExerciseBg = Color(0xFFF6F9FC)

data class NativeExercise(
    val id:String, val name:String, val group:String, val equipment:String,
    val difficulty:String="", val mechanic:String="", val met:Double=0.0,
    val primaryMuscles:List<String> = emptyList(), val secondaryMuscles:List<String> = emptyList(),
    val instructions:List<String> = emptyList(), val tips:List<String> = emptyList(),
    val imageStart:String?=null, val imagePeak:String?=null, val imageMain:String?=null
)
data class NativeWorkoutSet(val exercise:NativeExercise,val reps:Int,val loadKg:Double,val timestamp:Long){ val volume get()=reps*loadKg }

private fun jsonStrings(a:org.json.JSONArray?):List<String> = if(a==null) emptyList() else (0 until a.length()).mapNotNull{a.optString(it).takeIf(String::isNotBlank)}
private fun pretty(s:String)=s.replace('_',' ').replaceFirstChar{it.uppercase()}
private fun exerciseNumber(value:Double):String = if(value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US,"%.1f",value)

private suspend fun loadRepDb(context:android.content.Context):List<NativeExercise> = withContext(Dispatchers.IO){
    runCatching {
        val root=JSONObject(context.assets.open("repdb/exercises.json").bufferedReader().use{it.readText()})
        val a=root.getJSONArray("exercises")
        (0 until a.length()).map { i ->
            val o=a.getJSONObject(i); val flat=o.optJSONObject("images")?.optJSONObject("flat")
            NativeExercise(
                id=o.getString("id"), name=o.optString("name_en",o.getString("id")),
                group=pretty(o.optString("body_part","Other")), equipment=pretty(o.optString("equipment","Bodyweight")),
                difficulty=pretty(o.optString("difficulty")), mechanic=pretty(o.optString("mechanic")), met=o.optDouble("met",0.0),
                primaryMuscles=jsonStrings(o.optJSONArray("primary_muscles")), secondaryMuscles=jsonStrings(o.optJSONArray("secondary_muscles")),
                instructions=jsonStrings(o.optJSONArray("instructions_en")), tips=jsonStrings(o.optJSONArray("tips_en")),
                imageStart=flat?.optString("start")?.takeIf(String::isNotBlank), imagePeak=flat?.optString("peak")?.takeIf(String::isNotBlank), imageMain=flat?.optString("main")?.takeIf(String::isNotBlank)
            )
        }
    }.getOrElse { emptyList() }
}

@Composable private fun RepDbImage(path:String?, modifier:Modifier){
    val context=LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,path){ value=withContext(Dispatchers.IO){ path?.let{runCatching{context.assets.open("repdb/$it").use(BitmapFactory::decodeStream)}.getOrNull()} } }
    Box(modifier.background(Color(0xFFF1F6FA),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){
        if(bitmap!=null) Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit) else Text("EX",color=ExerciseBlue,fontWeight=FontWeight.Black)
    }
}

@Composable internal fun NativeExerciseParityScreen(onBack:()->Unit,openLegacy:()->Unit){
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var catalog by remember{mutableStateOf<List<NativeExercise>>(emptyList())}; var query by remember{mutableStateOf("")}; var selected by remember{mutableStateOf<NativeExercise?>(null)}
    var repsText by remember{mutableStateOf("10")}; var loadText by remember{mutableStateOf("0")}; var status by remember{mutableStateOf("Loading exercise library…")}; var showCount by remember{mutableStateOf(12)}
    val session=remember{mutableStateListOf<NativeWorkoutSet>()}; var recent by remember{mutableStateOf<List<HealthValue>>(emptyList())}
    suspend fun refresh(){ val n=System.currentTimeMillis(); recent=NativeDataHub.between("exercise_set",n-90L*86400000L,n).sortedByDescending{it.timestampEpochMs}.take(30) }
    LaunchedEffect(Unit){ catalog=loadRepDb(context); selected=catalog.firstOrNull(); status=if(catalog.isEmpty())"Exercise dataset unavailable" else "${catalog.size} exercises ready offline"; refresh() }
    val filtered=remember(catalog,query){ val q=query.trim().lowercase(); if(q.isBlank())catalog else catalog.filter{e -> listOf(e.name,e.group,e.equipment,e.difficulty,e.mechanic).any{it.lowercase().contains(q)} || e.primaryMuscles.any{it.contains(q,true)} } }
    val current=selected; val volume=session.sumOf{it.volume}; val best=current?.let{c->recent.filter{it.metadata["exerciseId"]==c.id}.mapNotNull{it.metadata["loadKg"]?.toDoubleOrNull()}.maxOrNull()}
    Column(Modifier.fillMaxSize().background(ExerciseBg).verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){ Box(Modifier.superhumanTopButton(onClick=onBack),contentAlignment=Alignment.Center){Text("←",color=ExerciseBlue,fontSize=28.sp,fontWeight=FontWeight.Bold)}; Spacer(Modifier.width(12.dp)); Column{Text("Exercise",color=ExerciseInk,fontSize=24.sp,fontWeight=FontWeight.Black);Text("Library, form, sets & progression",color=ExerciseMuted,fontSize=10.sp)} }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){ExerciseStat("SETS",session.size.toString(),Modifier.weight(1f));ExerciseStat("VOLUME",if(volume>0)"${volume.roundToInt()} kg" else "—",Modifier.weight(1f));ExerciseStat("PR",best?.let{"${exerciseNumber(it)} kg"}?:"—",Modifier.weight(1f))}
        Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(22.dp)).padding(16.dp)){
            Text("Exercise library",color=ExerciseInk,fontSize=18.sp,fontWeight=FontWeight.Black);Text("${catalog.size} illustrated exercises · search exercise, muscle or equipment",color=ExerciseMuted,fontSize=9.sp);Spacer(Modifier.height(9.dp))
            OutlinedTextField(query,{query=it;showCount=12},Modifier.fillMaxWidth(),singleLine=true,label={Text("Search exercises")});Spacer(Modifier.height(8.dp))
            filtered.take(showCount).forEach{e-> Row(Modifier.fillMaxWidth().clickable{selected=e}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){RepDbImage(e.imageMain?:e.imageStart,Modifier.size(58.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(e.name,color=ExerciseInk,fontSize=12.sp,fontWeight=FontWeight.ExtraBold);Text("${e.group} · ${e.equipment}",color=ExerciseMuted,fontSize=8.sp);if(e.primaryMuscles.isNotEmpty())Text(e.primaryMuscles.take(3).joinToString(" · "){pretty(it)},color=ExerciseBlue,fontSize=8.sp)};if(selected?.id==e.id)Text("✓",color=ExerciseGreen,fontWeight=FontWeight.Black)} }
            if(filtered.size>showCount) Box(Modifier.fillMaxWidth().clickable{showCount+=12}.padding(10.dp),contentAlignment=Alignment.Center){Text("LOAD 12 MORE",color=ExerciseBlue,fontSize=10.sp,fontWeight=FontWeight.Black)}
        }
        if(current!=null){
            Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(22.dp)).padding(16.dp)){Text(current.name,color=ExerciseInk,fontSize=20.sp,fontWeight=FontWeight.Black);Text("${current.difficulty} · ${current.mechanic} · ${current.equipment}${if(current.met>0)" · ${exerciseNumber(current.met)} MET" else ""}",color=ExerciseMuted,fontSize=9.sp);Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){RepDbImage(current.imageMain?:current.imageStart,Modifier.weight(1f).height(150.dp));if(current.imagePeak!=null)RepDbImage(current.imagePeak,Modifier.weight(1f).height(150.dp))};if(current.instructions.isNotEmpty()){Spacer(Modifier.height(12.dp));Text("HOW TO",color=ExerciseBlue,fontSize=9.sp,fontWeight=FontWeight.Black);current.instructions.take(6).forEachIndexed{i,s->Text("${i+1}. $s",color=ExerciseInk,fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=5.dp))};if(current.tips.isNotEmpty()){Spacer(Modifier.height(10.dp));Text("FORM TIPS",color=ExerciseGreen,fontSize=9.sp,fontWeight=FontWeight.Black);current.tips.take(3).forEach{Text("• $it",color=ExerciseMuted,fontSize=9.sp,modifier=Modifier.padding(top=4.dp))}}}
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFFFFF4E8),RoundedCornerShape(22.dp)).padding(16.dp)){Text("Log set",color=ExerciseOrange,fontSize=18.sp,fontWeight=FontWeight.Black);Text(current.name,color=ExerciseInk,fontSize=12.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(9.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(repsText,{repsText=it.filter(Char::isDigit).take(3)},Modifier.weight(1f),singleLine=true,label={Text("Reps")});OutlinedTextField(loadText,{loadText=it.filter{c->c.isDigit()||c=='.'}.take(6)},Modifier.weight(1f),singleLine=true,label={Text("Load kg")})};Spacer(Modifier.height(10.dp));Box(Modifier.fillMaxWidth().background(ExerciseOrange,RoundedCornerShape(16.dp)).clickable{val r=repsText.toIntOrNull()?:0;val l=loadText.toDoubleOrNull()?:0.0;if(r<1)status="Enter at least 1 rep" else {val set=NativeWorkoutSet(current,r,l,System.currentTimeMillis());session.add(set);scope.launch{NativeDataHub.saveValues(listOf(HealthValue(HealthDomain.EXERCISE,"exercise_set",set.volume,"kg-reps",set.timestamp,"repdb-exercise",mapOf("exerciseId" to current.id,"exerciseName" to current.name,"group" to current.group,"equipment" to current.equipment,"reps" to r.toString(),"loadKg" to l.toString(),"met" to current.met.toString()))));refresh();status="Saved ${current.name}"}}}.padding(14.dp),contentAlignment=Alignment.Center){Text("ADD SET",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Black)};Spacer(Modifier.height(6.dp));Text(status,color=ExerciseMuted,fontSize=9.sp)}
        }
        if(session.isNotEmpty()) Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(20.dp)).padding(15.dp)){Text("Current workout",color=ExerciseInk,fontSize=14.sp,fontWeight=FontWeight.ExtraBold);session.takeLast(10).forEach{set->Row(Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(set.exercise.name,color=ExerciseInk,fontSize=10.sp,modifier=Modifier.weight(1f));Text("${set.reps} × ${if(set.loadKg>0)exerciseNumber(set.loadKg)+" kg" else "BW"}",color=ExerciseMuted,fontSize=9.sp)}};Spacer(Modifier.height(8.dp));Box(Modifier.fillMaxWidth().background(ExerciseGreen,RoundedCornerShape(15.dp)).clickable{val now=System.currentTimeMillis();scope.launch{NativeDataHub.saveValues(listOf(HealthValue(HealthDomain.EXERCISE,"workout_session",session.size.toDouble(),"sets",now,"repdb-exercise",mapOf("volumeKg" to volume.toString()))));session.clear();status="Workout saved"}}.padding(13.dp),contentAlignment=Alignment.Center){Text("FINISH WORKOUT",color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Black)}}
        Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(20.dp)).padding(15.dp)){Text("Recent sets",color=ExerciseInk,fontSize=14.sp,fontWeight=FontWeight.ExtraBold);if(recent.isEmpty())Text("No workout sets yet.",color=ExerciseMuted,fontSize=10.sp) else recent.take(8).forEach{v->Row(Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(v.metadata["exerciseName"]?:"Exercise",color=ExerciseInk,fontSize=10.sp,modifier=Modifier.weight(1f));Text("${v.metadata["reps"]?:"—"} reps",color=ExerciseMuted,fontSize=9.sp)}}}
        Text("Exercise data & illustrations by RepDB · repdb.co",color=ExerciseMuted,fontSize=8.sp,modifier=Modifier.padding(6.dp));Spacer(Modifier.height(18.dp))
    }
}

@Composable private fun ExerciseStat(label:String,value:String,modifier:Modifier){Column(modifier.background(Color.White,RoundedCornerShape(16.dp)).padding(11.dp)){Text(label,color=ExerciseMuted,fontSize=8.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));Text(value,color=ExerciseNavy,fontSize=13.sp,fontWeight=FontWeight.Black)}}