#include <jni.h>
#include "transcribe.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
struct Session {
    std::mutex mutex;
    transcribe_session *session = nullptr;
    ~Session() { transcribe_session_free(session); }
};
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Session>> registry;
jlong nextHandle = 1;
void fail(JNIEnv *e, const char *type, const char *msg) {
    if (!e->ExceptionCheck()) { jclass c=e->FindClass(type); if(c) e->ThrowNew(c,msg); }
}
void state(JNIEnv *e,const char *msg){fail(e,"java/lang/IllegalStateException",msg);}
// Avoid JNI Modified UTF-8 for paths (including supplementary Unicode).
std::string pathUtf8(JNIEnv *e,jstring s) {
    const jsize n=e->GetStringLength(s);
    const jchar *p=e->GetStringChars(s,nullptr);
    if(!p) return {};
    std::string out;
    try {
        for(jsize i=0;i<n;i++) {
            uint32_t c=p[i];
            if(c==0) throw 1;
            if(c>=0xd800 && c<=0xdbff) {
                if(i+1>=n || p[i+1]<0xdc00 || p[i+1]>0xdfff) throw 1;
                c=0x10000+((c-0xd800)<<10)+(p[++i]-0xdc00);
            } else if(c>=0xdc00 && c<=0xdfff) throw 1;
            if(c<0x80) out+=char(c);
            else if(c<0x800){out+=char(0xc0|(c>>6));out+=char(0x80|(c&63));}
            else if(c<0x10000){out+=char(0xe0|(c>>12));out+=char(0x80|((c>>6)&63));out+=char(0x80|(c&63));}
            else{out+=char(0xf0|(c>>18));out+=char(0x80|((c>>12)&63));out+=char(0x80|((c>>6)&63));out+=char(0x80|(c&63));}
        }
    } catch(...) { e->ReleaseStringChars(s,p); throw; }
    e->ReleaseStringChars(s,p); return out;
}
jstring textUtf8(JNIEnv *e,const char *s) {
    // Java's UTF-8 decoder is length-aware and replaces malformed sequences.
    size_t n=std::char_traits<char>::length(s);
    if(n>INT32_MAX) {state(e,"ASR result too large");return nullptr;}
    jbyteArray bytes=e->NewByteArray(static_cast<jsize>(n)); if(!bytes)return nullptr;
    e->SetByteArrayRegion(bytes,0,static_cast<jsize>(n),reinterpret_cast<const jbyte*>(s));
    jclass cls=e->FindClass("java/lang/String"); if(!cls)return nullptr;
    jmethodID ctor=e->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");if(!ctor)return nullptr;
    jstring charset=e->NewStringUTF("UTF-8");if(!charset)return nullptr;
    return static_cast<jstring>(e->NewObject(cls,ctor,bytes,charset));
}
}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*,void*) {
    transcribe_log_set(nullptr,nullptr); return JNI_VERSION_1_6;
}
extern "C" JNIEXPORT jlong JNICALL
Java_io_github_mangi_eta_agent_voice_local_GgufSpeechNative_load(JNIEnv *e,jobject,jstring path,jint threads) {
    try {
        if(!path){fail(e,"java/lang/IllegalArgumentException","Missing model path");return 0;}
        std::string p=pathUtf8(e,path);if(e->ExceptionCheck())return 0;
        if(p.empty()||p[0]!='/'){fail(e,"java/lang/IllegalArgumentException","Invalid model path");return 0;}
        auto owner=std::make_shared<Session>();
        transcribe_model_load_params lp;transcribe_model_load_params_init(&lp);lp.backend=TRANSCRIBE_BACKEND_CPU;
        transcribe_session_params sp;transcribe_session_params_init(&sp);sp.n_threads=std::clamp<int>(threads,1,3);
        auto status=transcribe_open(p.c_str(),&lp,&sp,&owner->session);
        if(status!=TRANSCRIBE_OK){state(e,"GGUF model load failed");return 0;}
        std::lock_guard<std::mutex> lock(registryMutex);
        if(nextHandle==INT64_MAX){state(e,"ASR handle limit reached");return 0;}
        jlong h=nextHandle++;registry.emplace(h,std::move(owner));return h;
    } catch(const std::bad_alloc&){fail(e,"java/lang/OutOfMemoryError","ASR allocation failed");}
      catch(...){state(e,"GGUF model load failed");}
    return 0;
}
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_mangi_eta_agent_voice_local_GgufSpeechNative_recognize(JNIEnv *e,jobject,jlong h,jfloatArray pcm) {
    try {
        std::shared_ptr<Session> s;
        {std::lock_guard<std::mutex> lock(registryMutex);auto it=registry.find(h);if(it!=registry.end())s=it->second;}
        if(!s){state(e,"Invalid ASR handle");return nullptr;}
        if(!pcm){fail(e,"java/lang/IllegalArgumentException","Missing PCM");return nullptr;}
        jsize n=e->GetArrayLength(pcm);
        if(n<1||n>480000){fail(e,"java/lang/IllegalArgumentException","PCM must contain 1..480000 samples");return nullptr;}
        std::vector<float> samples(n);e->GetFloatArrayRegion(pcm,0,n,samples.data());if(e->ExceptionCheck())return nullptr;
        for(float v:samples)if(!std::isfinite(v)||v < -1.0f || v > 1.0f){fail(e,"java/lang/IllegalArgumentException","PCM must be finite and normalized");return nullptr;}
        std::lock_guard<std::mutex> lock(s->mutex);
        if(!s->session){state(e,"Released ASR handle");return nullptr;}
        transcribe_run_params rp;transcribe_run_params_init(&rp);
        if(transcribe_run(s->session,samples.data(),n,&rp)!=TRANSCRIBE_OK){state(e,"GGUF recognition failed");return nullptr;}
        return textUtf8(e,transcribe_full_text(s->session));
    } catch(const std::bad_alloc&){fail(e,"java/lang/OutOfMemoryError","ASR allocation failed");}
      catch(...){state(e,"GGUF recognition failed");}
    return nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_mangi_eta_agent_voice_local_GgufSpeechNative_release(JNIEnv *e,jobject,jlong h) {
    try {
        std::shared_ptr<Session> s;
        {std::lock_guard<std::mutex> lock(registryMutex);auto it=registry.find(h);if(it==registry.end())return;s=it->second;registry.erase(it);}
        std::lock_guard<std::mutex> lock(s->mutex);
        transcribe_session_free(s->session);s->session=nullptr;
    } catch(...){state(e,"GGUF release failed");}
}
