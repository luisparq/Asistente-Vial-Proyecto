#pragma once
#include <Arduino.h>
#include <Preferences.h>
#include <math.h>
#include <algorithm>
#include <memory>
#include <new>

// Experimental onboard envelope learner. No phone/server is involved in detection.
namespace VehicleControl {
struct Vec { float x,y,z; };
inline Vec sub(Vec a,Vec b){return {a.x-b.x,a.y-b.y,a.z-b.z};}
inline Vec mul(Vec a,float b){return {a.x*b,a.y*b,a.z*b};}
inline float dot(Vec a,Vec b){return a.x*b.x+a.y*b.y+a.z*b.z;}
inline float norm(Vec a){return sqrtf(dot(a,a));}
inline Vec unit(Vec a){return mul(a,1.0f/norm(a));}
struct Profile {
  uint32_t magic=0x41564734;
  Vec rest{},bias{},forward{};
  float accel=0,brake=0,turn=0;
  float normalAccel=0,normalBrake=0,normalTurn=0;
  uint16_t version=0;
  bool ready=false,enabled=false;
};
Profile profile;
Preferences prefs;
bool storageReady=false;
uint8_t stage=0,lessons=0,range=4,event=0;
const char *status="NEEDS_REST";
char acknowledged[37]={0};
char recent[16][37]={};uint8_t recentIndex=0;
bool duplicateCommand(const char *id){
 bool duplicate=false;for(int i=0;i<16;i++)if(strcmp(recent[i],id)==0)duplicate=true;
 strlcpy(acknowledged,id,37);if(!duplicate){strlcpy(recent[recentIndex++%16],id,37);}return duplicate;
}
uint32_t started=0,last=0,candidateAt=0,lastEvent=0;
bool hadSample=false,hadEvent=false,invalid=false,restReady=false,finishRequested=false;
uint8_t candidate=0;
uint16_t evidence=0,count=0;
float means[6]={},m2[6]={};
constexpr uint16_t MAX_LESSON_SAMPLES = 1200;
std::unique_ptr<Vec[]> lessonVectors;
float normalA=0,normalB=0,normalT=0;
bool persist(){
  if(storageReady && prefs.putBytes("profile",&profile,sizeof(profile))==sizeof(profile))return true;
  profile.enabled=false;profile.ready=false;status="PERSISTENCE_ERROR";return false;
}
bool valid(const Profile &p){
  return p.magic==0x41564734 && (!p.ready ? !p.enabled : (p.version>0 && norm(p.rest)>0.7f && norm(p.rest)<1.3f &&
    fabsf(norm(p.forward)-1)<0.02f && fabsf(dot(p.forward,unit(p.rest)))<0.03f &&
    isfinite(norm(p.bias)) && isfinite(p.accel) && isfinite(p.brake) && isfinite(p.turn) &&
    p.accel>0 && p.accel<2 && p.brake>0 && p.brake<2 && p.turn>0 && p.turn<250 &&
    isfinite(p.normalAccel) && p.normalAccel>0 && p.normalAccel<p.accel &&
    isfinite(p.normalBrake) && p.normalBrake>0 && p.normalBrake<p.brake &&
    isfinite(p.normalTurn) && p.normalTurn>0 && p.normalTurn<p.turn));
}
void begin(){
  storageReady=prefs.begin("avigem-motion",false);
  if(!storageReady){status="PERSISTENCE_ERROR";return;}
  if(prefs.getBytesLength("profile")==sizeof(Profile)){
    Profile saved; prefs.getBytes("profile",&saved,sizeof(saved));
    if(valid(saved)) profile=saved;
  }
  restReady=profile.ready;lessons=profile.ready?7:0;
  normalA=profile.normalAccel;normalB=profile.normalBrake;normalT=profile.normalTurn;
  status=profile.ready?"PROFILE_LOADED":"NEEDS_REST";
}
void resetEvidence(){candidate=0;evidence=0;hadSample=false;}
void command(const char *id,const char *op,uint32_t now){
  if(duplicateCommand(id))return;
  if(strcmp(op,"disable")==0){stage=0;lessonVectors.reset();profile.enabled=false;range=4;event=0;resetEvidence();if(persist())status="DISABLED";return;}
  if(strcmp(op,"enable")==0){if(profile.ready){profile.enabled=true;resetEvidence();if(persist())status="ENABLED";}else status="NEEDS_CALIBRATION";return;}
  if(strcmp(op,"finish")==0){if(stage>1)finishRequested=true;return;}
  uint8_t next=strcmp(op,"rest")==0?1:strcmp(op,"acceleration")==0?2:strcmp(op,"braking")==0?3:strcmp(op,"turn")==0?4:0;
  if(!next){status="UNKNOWN_COMMAND";return;}
  if(stage){status="FINISH_CURRENT_LESSON";return;}
  if(next!=1 && (!restReady || (next>2 && !(lessons&1)))){status="NEEDS_REST_AND_FORWARD";return;}
  std::unique_ptr<Vec[]> samples;
  if(next>1){
    samples.reset(new (std::nothrow) Vec[MAX_LESSON_SAMPLES]);
    if(!samples){status="LESSON_MEMORY_UNAVAILABLE";return;}
  }
  profile.enabled=false;profile.ready=false;range=4;event=0;if(!persist())return;
  lessonVectors=std::move(samples);
  stage=next;started=now;count=0;finishRequested=false;invalid=false;resetEvidence();
  memset(means,0,sizeof(means));memset(m2,0,sizeof(m2));
  if(next==1){restReady=false;lessons=0;}
  if(next==2)lessons=0;
  if(next==3)lessons&=~2;
  if(next==4)lessons&=~4;
  status="LEARNING";
}
float p95(Vec *values,uint16_t n){
  std::sort(values,values+n,[](const Vec &a,const Vec &b){return a.x<b.x;});
  return values[(uint16_t)ceilf((n-1)*0.95f)].x;
}
void finish(){
  if(!stage)return;
  auto samples=std::move(lessonVectors);
  Vec *vectors=samples.get();
  uint8_t completed=stage;stage=0;resetEvidence();
  if(invalid || count<(completed==1?100:12)){status="INSUFFICIENT_OR_INTERRUPTED";return;}
  if(completed==1){
    for(int i=0;i<6;i++)if(sqrtf(fmaxf(0,m2[i]/(count-1)))>(i<3?0.02f:1.0f)){status="REST_MOVED";return;}
    profile.rest={means[0],means[1],means[2]};profile.bias={means[3],means[4],means[5]};
    if(norm(profile.rest)<0.7f || norm(profile.rest)>1.3f){status="INVALID_GRAVITY";return;}
    restReady=true;status="REST_READY";return;
  }
  uint16_t useful=0;Vec up=unit(profile.rest);
  if(completed==2){
    Vec sum={0,0,0};float lengths=0;
    for(int i=0;i<count;i++)if(norm(vectors[i])>0.03f){sum.x+=vectors[i].x;sum.y+=vectors[i].y;sum.z+=vectors[i].z;lengths+=norm(vectors[i]);}
    if(lengths==0 || norm(sum)/lengths<0.8f){status="REPEAT_STRAIGHT_ACCELERATION";return;}
    profile.forward=unit(sum);
    for(int i=0;i<count;i++){float v=dot(vectors[i],profile.forward);if(v>0.03f)vectors[useful++].x=v;}
    if(useful>=8){normalA=p95(vectors,useful);lessons|=1;}
  }else if(completed==3){
    for(int i=0;i<count;i++){float v=-dot(vectors[i],profile.forward);if(v>0.03f)vectors[useful++].x=v;}
    if(useful>=8){normalB=p95(vectors,useful);lessons|=2;}
  }else{
    int positive=0,negative=0;
    for(int i=0;i<count;i++){float v=vectors[i].x;if(v>2)positive++;if(v< -2)negative++;if(fabsf(v)>2)vectors[useful++].x=fabsf(v);}
    if(positive<4 || negative<4){status="NEEDS_LEFT_AND_RIGHT_TURNS";return;}
    if(useful>=8){normalT=p95(vectors,useful);lessons|=4;}
  }
  if(useful<8){status="MANEUVER_NOT_OBSERVED";return;}
  status="LESSON_READY";
  if(lessons==7){
    profile.accel=fmaxf(0.12f,normalA*1.8f);profile.brake=fmaxf(0.12f,normalB*1.8f);profile.turn=fmaxf(10,normalT*1.8f);
    if(profile.version==UINT16_MAX){status="PROFILE_VERSION_EXHAUSTED";return;}
    const uint16_t oldVersion=profile.version;
    profile.normalAccel=normalA;profile.normalBrake=normalB;profile.normalTurn=normalT;
    profile.ready=true;profile.enabled=true;profile.version++;
    if(!valid(profile)){profile.ready=false;profile.enabled=false;profile.version=oldVersion;status="PROFILE_OUT_OF_SENSOR_RANGE";return;}
    if(persist()){range=0;status="PROFILE_APPLIED";}else profile.version=oldVersion;
  }
}
void sensorFailure(){
  if(stage)invalid=true;
  range=4;event=0;resetEvidence();
}
void poll(uint32_t now){
  if(!stage)return;
  bool due=uint32_t(now-started)>=(stage==1?30000:60000) || (stage>1 && finishRequested && uint32_t(now-started)>=3000);
  if(due){if(!hadSample || uint32_t(now-last)>250)invalid=true;finish();}
}
// Returns an event code once, after sustained evidence. range reports current signal separately.
uint8_t accept(const int16_t raw[6],uint32_t now){
  event=0;
  for(int i=0;i<6;i++)if(raw[i]>=32760 || raw[i]<=-32760){sensorFailure();return 0;}
  Vec a={raw[0]/16384.f,raw[1]/16384.f,raw[2]/16384.f};Vec g={raw[3]/131.f,raw[4]/131.f,raw[5]/131.f};
  if(hadSample && uint32_t(now-last)>250){invalid=true;candidate=0;evidence=0;}last=now;hadSample=true;
  if(stage){
    range=4;
    if(count<MAX_LESSON_SAMPLES){
      if(stage==1){float v[]={a.x,a.y,a.z,g.x,g.y,g.z};for(int i=0;i<6;i++){float d=v[i]-means[i];means[i]+=d/(count+1);m2[i]+=d*(v[i]-means[i]);}}
      else{Vec up=unit(profile.rest),d=sub(a,profile.rest);lessonVectors[count]=stage==4?Vec{dot(sub(g,profile.bias),up),0,0}:sub(d,mul(up,dot(d,up)));}
      count++;
    }
    poll(now);
    return 0;
  }
  if(!profile.ready || !profile.enabled){range=4;return 0;}
  float longitudinal=dot(sub(a,profile.rest),profile.forward),yaw=fabsf(dot(sub(g,profile.bias),unit(profile.rest)));
  range=yaw>profile.turn?3:longitudinal>profile.accel?1:-longitudinal>profile.brake?2:0;
  if(!range || (hadEvent && uint32_t(now-lastEvent)<3000)){candidate=0;evidence=0;return 0;}
  if(candidate!=range){candidate=range;candidateAt=now;evidence=0;}
  evidence++;
  if(evidence>=3 && uint32_t(now-candidateAt)>=300){event=range;hadEvent=true;lastEvent=now;candidate=0;evidence=0;}
  return event;
}
uint8_t flags(){return (profile.ready?1:0)|(profile.enabled?2:0)|(stage<<2);}
}
