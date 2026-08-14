package br.com.falhauniverso.oitonovedez;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;

public class StableAnalysisService extends Service{
    public static final String PREF="stable_analysis_v14";
    private static final String CHANNEL="stable_analysis_channel";
    private volatile boolean workerRunning=false;

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(
                CHANNEL,"Falha Universo em execução",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Mantém a análise ativa com a tela bloqueada.");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification note(String text,int pct){
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ? new Notification.Builder(this,CHANNEL)
            : new Notification.Builder(this);

        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(
            this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        return b.setContentTitle("Falha Universo 8/9/10")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100,Math.max(0,Math.min(100,pct)),false)
            .setContentIntent(pi)
            .build();
    }

    private void updateNote(String text,int pct){
        try{
            getSystemService(NotificationManager.class).notify(92,note(text,pct));
        }catch(Exception ignored){}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        SharedPreferences sp=getSharedPreferences(PREF,MODE_PRIVATE);

        if(intent!=null && "START".equals(intent.getAction())){
            String uri=intent.getStringExtra("uri");
            int mode=intent.getIntExtra("mode",-1);
            int selected=intent.getIntExtra("selectedRepeat",mode);

            if(mode!=0 && mode!=8 && mode!=9 && mode!=10){
                sp.edit().putBoolean("running",false).putString("error","Módulo inválido: "+mode).apply();
                return START_NOT_STICKY;
            }
            if(mode!=0 && selected!=mode){
                sp.edit().putBoolean("running",false)
                  .putString("error","Roteamento divergente: botão "+selected+" / serviço "+mode).apply();
                return START_NOT_STICKY;
            }

            sp.edit()
              .putString("uri",uri==null?"":uri)
              .putInt("mode",mode)
              .putInt("selectedRepeat",selected)
              .putInt("currentRepeat",mode==0?8:mode)
              .putBoolean("running",true)
              .putBoolean("done",false)
              .putString("error","")
              .putLong("processed",0)
              .putLong("inside",0)
              .putInt("bestGame",0)
              .putLong("bestScoreBits",Double.doubleToLongBits(-1e100))
              .putLong("startedAt",System.currentTimeMillis())
              .apply();
        }

        if(!workerRunning && sp.getBoolean("running",false)){
            try{
                startForeground(92,note("Preparando análise...",0));
                workerRunning=true;
                new Thread(this::runWorker,"StableFalhaWorker").start();
            }catch(Throwable e){
                sp.edit().putBoolean("running",false)
                  .putString("error","Falha ao iniciar serviço: "+String.valueOf(e.getMessage()))
                  .apply();
                workerRunning=false;
            }
        }

        return START_STICKY;
    }

    private CoreEngine.Model loadModel(String uriS)throws Exception{
        if(uriS==null||uriS.isEmpty())throw new IllegalStateException("TXT não está salvo.");
        Uri uri=Uri.parse(uriS);
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new FileNotFoundException("Não foi possível reabrir o TXT.");
            return new CoreEngine.Model(CoreEngine.parseHistory(in));
        }
    }

    private void clearCheckpointForRepeat(SharedPreferences sp,int repeat){
        sp.edit()
          .putInt("currentRepeat",repeat)
          .putLong("processed",0)
          .putLong("inside",0)
          .putInt("bestGame",0)
          .putLong("bestScoreBits",Double.doubleToLongBits(-1e100))
          .apply();
    }

    private void saveResult(SharedPreferences sp,CoreEngine.UniverseResult r){
        sp.edit()
          .putString("report"+r.repeats,r.report)
          .putInt("failure"+r.repeats,r.failure10)
          .putInt("game"+r.repeats,r.game15)
          .putLong("total"+r.repeats,r.totalUniverse)
          .putLong("insideResult"+r.repeats,r.insidePattern)
          .putLong("scoreBits"+r.repeats,Double.doubleToLongBits(r.score))
          .apply();
    }

    private CoreEngine.UniverseResult runOne(
        CoreEngine.Model model,int repeat,SharedPreferences sp,long started)throws Exception{

        long resumeProcessed=sp.getLong("processed",0);
        long resumeInside=sp.getLong("inside",0);
        int resumeBestGame=sp.getInt("bestGame",0);
        double resumeBestScore=Double.longBitsToDouble(
            sp.getLong("bestScoreBits",Double.doubleToLongBits(-1e100)));

        return CoreEngine.module3ForRepeatResumeStable(
            model,repeat,resumeProcessed,resumeInside,
            resumeBestGame,resumeBestScore,
            (pct,msg,processed,inside,bestGame,bestScore)->{
                long elapsed=Math.max(1,System.currentTimeMillis()-started);
                long eta=pct>.1?(long)(elapsed*(100.0/pct-1.0)):-1;

                sp.edit()
                  .putLong("processed",processed)
                  .putLong("inside",inside)
                  .putInt("bestGame",bestGame)
                  .putLong("bestScoreBits",Double.doubleToLongBits(bestScore))
                  .putFloat("pct",(float)pct)
                  .putString("msg",msg)
                  .putLong("elapsed",elapsed)
                  .putLong("eta",eta)
                  .apply();

                updateNote(msg+" • "+String.format(Locale.US,"%.1f%%",pct),(int)pct);
            });
    }

    private void runWorker(){
        SharedPreferences sp=getSharedPreferences(PREF,MODE_PRIVATE);
        try{
            CoreEngine.Model model=loadModel(sp.getString("uri",""));
            int mode=sp.getInt("mode",-1);
            int selected=sp.getInt("selectedRepeat",mode);
            long started=sp.getLong("startedAt",System.currentTimeMillis());

            if(mode!=0 && mode!=8 && mode!=9 && mode!=10)
                throw new IllegalStateException("Modo salvo inválido: "+mode);
            if(mode!=0 && selected!=mode)
                throw new IllegalStateException("Roteamento salvo divergente: "+selected+" / "+mode);

            sp.edit().putString("msg",
                mode==0 ? "Executando 8 + 9 + 10 repetidas" : "Executando SOMENTE "+mode+" repetidas").apply();

            if(mode==0){
                int current=sp.getInt("currentRepeat",8);
                for(int repeat=current;repeat<=10;repeat++){
                    if(repeat!=8&&repeat!=9&&repeat!=10)continue;

                    if(sp.getInt("currentRepeat",8)!=repeat)
                        clearCheckpointForRepeat(sp,repeat);

                    CoreEngine.UniverseResult r=runOne(model,repeat,sp,started);
                    saveResult(sp,r);

                    if(repeat==8)clearCheckpointForRepeat(sp,9);
                    else if(repeat==9)clearCheckpointForRepeat(sp,10);
                }
            }else{
                clearCheckpointForRepeat(sp,mode);
                CoreEngine.UniverseResult r=runOne(model,mode,sp,started);
                if(r.repeats!=mode)
                    throw new IllegalStateException("Motor retornou universo "+r.repeats+" mas foi solicitado "+mode);
                saveResult(sp,r);
            }

            sp.edit()
              .putBoolean("running",false)
              .putBoolean("done",true)
              .putFloat("pct",100f)
              .putString("msg","Concluído")
              .putLong("eta",0)
              .apply();

            updateNote("Análise concluída",100);

        }catch(Throwable e){
            sp.edit()
              .putBoolean("running",false)
              .putString("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()))
              .apply();
            updateNote("Erro na análise",0);
        }finally{
            workerRunning=false;
            try{
                if(Build.VERSION.SDK_INT>=24)
                    stopForeground(STOP_FOREGROUND_DETACH);
                else
                    stopForeground(false);
            }catch(Exception ignored){}
        }
    }

    @Override public IBinder onBind(Intent i){return null;}
}
