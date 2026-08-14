package br.com.falhauniverso.oitonovedez;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity{
    final ExecutorService ex=Executors.newSingleThreadExecutor();
    final Handler handler=new Handler(Looper.getMainLooper());
    CoreEngine.Model model;
    CoreEngine.ModuleResult lastResult;
    TextView status,clock,trendView,out;
    ProgressBar bar;
    Button m1,m2,m3,m8,m9,m10,failTableBtn,resetBtn;
    LinearLayout actions;
    long startedAt=0;double pct=0;boolean running=false;
    int purple=0xFF8E44AD;

    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int z,boolean b){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(dp(10),dp(8),dp(10),dp(8));
        if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;
    }
    Button bt(String s){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundColor(purple);return b;
    }

    public void onCreate(Bundle x){
        super.onCreate(x);
        ScrollView sv=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(40));sv.addView(root);

        TextView hd=tv("FALHA DO UNIVERSO 8 / 9 / 10",23,true);
        hd.setTextColor(Color.WHITE);hd.setBackgroundColor(purple);root.addView(hd);
        root.addView(tv("Aplicativo independente • padrão → falha → evolução • PDF",13,true));

        Button load=bt("CARREGAR NOVO TXT — RESETAR E ANALISAR");root.addView(load,new LinearLayout.LayoutParams(-1,dp(62)));
        load.setOnClickListener(v->pick());

        status=tv("Aguardando TXT.",14,true);root.addView(status);
        bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(18)));
        clock=tv("Progresso: 0,0% | Tempo: 00:00 | ETA: --:--",13,true);root.addView(clock);

        trendView=tv("Tendência de repetidas: carregue o TXT.",14,true);root.addView(trendView);

        failTableBtn=bt("VER TABELA DE FREQUÊNCIA DE FALHA");failTableBtn.setEnabled(false);
        root.addView(failTableBtn,new LinearLayout.LayoutParams(-1,dp(60)));
        failTableBtn.setOnClickListener(v->{if(model!=null){out.setText(CoreEngine.failureFrequencyReport(model));clearActions();}});

        m1=bt("ESTUDO AUXILIAR — 300 DUPLAS");m1.setEnabled(false);root.addView(m1,new LinearLayout.LayoutParams(-1,dp(66)));
        root.addView(tv("300 duplas de 01–25. Score 100/50/0, evolução, movimento e frequência individual de falha.",13,false));

        m2=bt("ESTUDO AUXILIAR — FALHAS HISTÓRICAS");m2.setEnabled(false);root.addView(m2,new LinearLayout.LayoutParams(-1,dp(66)));
        root.addView(tv("Em cada concurso, gera somente as 45 duplas das 10 falhas e acumula o banco real da falha.",13,false));

        m3=bt("MÓDULO GERAL — ANALISAR 8 / 9 / 10");m3.setEnabled(false);root.addView(m3,new LinearLayout.LayoutParams(-1,dp(66)));
        root.addView(tv("Executa os três universos em sequência.",13,false));

        m8=bt("MÓDULO 8 REPETIDAS — 772.200 JOGOS");m8.setEnabled(false);root.addView(m8,new LinearLayout.LayoutParams(-1,dp(64)));
        root.addView(tv("Analisa somente o universo de 8 repetidas e gera só o resultado/PDF de 8.",13,false));

        m9=bt("MÓDULO 9 REPETIDAS — 1.051.050 JOGOS");m9.setEnabled(false);root.addView(m9,new LinearLayout.LayoutParams(-1,dp(64)));
        root.addView(tv("Analisa somente o universo de 9 repetidas e gera só o resultado/PDF de 9.",13,false));

        m10=bt("MÓDULO 10 REPETIDAS — 756.756 JOGOS");m10.setEnabled(false);root.addView(m10,new LinearLayout.LayoutParams(-1,dp(64)));
        root.addView(tv("Analisa somente o universo de 10 repetidas e gera só o resultado/PDF de 10.",13,false));

        resetBtn=bt("ZERAR / NOVA ANÁLISE");root.addView(resetBtn,new LinearLayout.LayoutParams(-1,dp(58)));

        out=tv("",14,false);out.setTextIsSelectable(true);root.addView(out);
        actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);root.addView(actions);

        m1.setOnClickListener(v->runModule(1));
        m2.setOnClickListener(v->runModule(2));
        m3.setOnClickListener(v->runModule3());
        m8.setOnClickListener(v->startStableService(8));
        m9.setOnClickListener(v->startStableService(9));
        m10.setOnClickListener(v->startStableService(10));
        resetBtn.setOnClickListener(v->manualResetStable());
        setContentView(sv);
        new Handler(Looper.getMainLooper()).postDelayed(this::restoreStableState,150);
    }

    @Override protected void onResume(){
        super.onResume();
        if(status!=null)new Handler(Looper.getMainLooper()).postDelayed(this::restoreStableState,100);
    }

    void pick(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/*");startActivityForResult(i,10);
    }

    void hardReset(){
        running=false;pct=0;startedAt=0;bar.setProgress(0);
        status.setText("Carregando novo TXT...");
        clock.setText("Progresso: 0,0% | Tempo: 00:00 | ETA: --:--");
        trendView.setText("Tendência de repetidas: recalculando...");
        out.setText("");
        lastResult=null;
        clearActions();
        m1.setEnabled(false);m2.setEnabled(false);m3.setEnabled(false);m8.setEnabled(false);m9.setEnabled(false);m10.setEnabled(false);failTableBtn.setEnabled(false);
    }
    void clearActions(){actions.removeAllViews();}

    protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);if(r!=10||c!=RESULT_OK||d==null)return;
        hardReset();
        try{
            int flags=d.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(d.getData(),flags);
        }catch(Exception ignored){}
        getSharedPreferences(StableAnalysisService.PREF,MODE_PRIVATE).edit().clear().putString("uri",d.getData().toString()).apply();
        try(InputStream in=getContentResolver().openInputStream(d.getData())){
            List<int[]>x=CoreEngine.parseHistory(in);model=new CoreEngine.Model(x);
            CoreEngine.RepeatTrend rt=CoreEngine.repeatTrend(model);
            status.setText("TXT carregado: "+x.size()+" concursos. Base anterior zerada.");
            trendView.setText("Último movimento: "+rt.lastRepeat+" repetidas | Tendência indicada: "+rt.suggestion+
                " | confiança relativa: "+String.format(Locale.US,"%.1f%%",rt.confidence*100));
            m1.setEnabled(true);m2.setEnabled(true);m3.setEnabled(true);m8.setEnabled(true);m9.setEnabled(true);m10.setEnabled(true);failTableBtn.setEnabled(true);
        }catch(Exception e){model=null;status.setText("Erro: "+e.getMessage());}
    }

    void runModule(int mod){
        if(model==null)return;
        m1.setEnabled(false);m2.setEnabled(false);m3.setEnabled(false);m8.setEnabled(false);m9.setEnabled(false);m10.setEnabled(false);failTableBtn.setEnabled(false);
        out.setText("");clearActions();startProgress();
        ex.submit(()->{try{
            CoreEngine.Progress p=(a,b)->runOnUiThread(()->update(a,b));
            CoreEngine.ModuleResult rr=mod==1?CoreEngine.module1(model,p):CoreEngine.module2(model,p);
            runOnUiThread(()->{
                running=false;lastResult=rr;out.setText(rr.report);addPdfButton(rr);
                m1.setEnabled(true);m2.setEnabled(true);m3.setEnabled(true);m8.setEnabled(true);m9.setEnabled(true);m10.setEnabled(true);failTableBtn.setEnabled(true);
            });
        }catch(Exception e){runOnUiThread(()->{
            running=false;status.setText("Erro: "+e.getMessage());
            m1.setEnabled(true);m2.setEnabled(true);m3.setEnabled(true);m8.setEnabled(true);m9.setEnabled(true);m10.setEnabled(true);failTableBtn.setEnabled(true);
        });}});
    }



    void runSingleUniverse(int repeats){
        startStableService(repeats);
    }

    void startStableService(int mode){
        SharedPreferences sp=getSharedPreferences(StableAnalysisService.PREF,MODE_PRIVATE);
        String uri=sp.getString("uri","");
        if(uri.isEmpty()){
            Toast.makeText(this,"Carregue o TXT primeiro.",Toast.LENGTH_LONG).show();
            return;
        }
        if(sp.getBoolean("running",false)){
            Toast.makeText(this,"A análise já está rodando. Vou manter o checkpoint atual.",Toast.LENGTH_LONG).show();
            restoreStableState();
            return;
        }

        SharedPreferences.Editor ed=sp.edit()
          .putInt("mode",mode)
          .putBoolean("running",true)
          .putBoolean("done",false)
          .putString("error","")
          .putFloat("pct",0)
          .putString("msg","Preparando...")
          .putLong("processed",0)
          .putLong("inside",0)
          .putInt("bestGame",0)
          .putLong("bestScoreBits",Double.doubleToLongBits(-1e100))
          .putLong("startedAt",System.currentTimeMillis());

        for(int r:new int[]{8,9,10}){
            ed.remove("report"+r).remove("failure"+r).remove("game"+r)
              .remove("total"+r).remove("insideResult"+r).remove("scoreBits"+r);
        }
        ed.apply();

        status.setText(mode==0 ? "Selecionado: 8 + 9 + 10 repetidas" : "Selecionado: "+mode+" repetidas");
        Intent s=new Intent(this,StableAnalysisService.class);
        s.setAction("START");
        s.putExtra("uri",uri);
        s.putExtra("mode",mode);
        s.putExtra("selectedRepeat",mode);

        try{
            if(Build.VERSION.SDK_INT>=26)startForegroundService(s);
            else startService(s);
            Toast.makeText(this,"Análise iniciada. Pode bloquear a tela.",Toast.LENGTH_LONG).show();
        }catch(Throwable e){
            sp.edit().putBoolean("running",false)
              .putString("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()))
              .apply();
            Toast.makeText(this,"Não foi possível iniciar: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }

        restoreStableState();
    }

    void restoreStableState(){
        try{
            if(status==null||bar==null||clock==null||out==null)return;

            SharedPreferences sp=getSharedPreferences(StableAnalysisService.PREF,MODE_PRIVATE);
            boolean running=sp.getBoolean("running",false);
            boolean done=sp.getBoolean("done",false);
            float pct=sp.getFloat("pct",0);
            String msg=sp.getString("msg","");
            long elapsed=sp.getLong("elapsed",0);
            long eta=sp.getLong("eta",-1);

            bar.setProgress((int)(Math.max(0,Math.min(100,pct))*10));

            if(running)status.setText(msg.isEmpty()?"Análise em segundo plano...":msg);
            else if(done)status.setText("Análise concluída e salva.");
            else if(!sp.getString("error","").isEmpty())
                status.setText("Erro: "+sp.getString("error",""));

            clock.setText(
                "Progresso: "+String.format(Locale.US,"%.1f",pct).replace('.',',')+
                "% | Tempo: "+fmtStable(elapsed)+
                " | ETA: "+(eta<0?"--:--":fmtStable(eta)));

            boolean enable=!running && !sp.getString("uri","").isEmpty();
            m1.setEnabled(enable);m2.setEnabled(enable);m3.setEnabled(enable);
            m8.setEnabled(enable);m9.setEnabled(enable);m10.setEnabled(enable);
            failTableBtn.setEnabled(enable);
            resetBtn.setEnabled(!running);

            if(done){
                clearActions();
                StringBuilder sb=new StringBuilder();
                for(int r:new int[]{8,9,10}){
                    String rep=sp.getString("report"+r,"");
                    int f=sp.getInt("failure"+r,0);
                    if(!rep.isEmpty()){
                        if(sb.length()>0)sb.append("\n\n========================\n\n");
                        sb.append(rep);
                        if(f!=0){
                            long total=sp.getLong("total"+r,0);
                            long inside=sp.getLong("insideResult"+r,0);
                            double sc=Double.longBitsToDouble(
                                sp.getLong("scoreBits"+r,Double.doubleToLongBits(0)));
                            addUniversePdfButton(
                                new CoreEngine.UniverseResult(r,f,total,inside,sc,rep));
                        }
                    }
                }
                out.setText(sb.toString());
            }
        }catch(Throwable e){
            if(status!=null)
                status.setText("Estado salvo ignorado com segurança: "+e.getClass().getSimpleName());
        }
    }

    String fmtStable(long ms){
        long s=Math.max(0,ms/1000),mi=s/60;
        s%=60;
        return String.format(Locale.US,"%02d:%02d",mi,s);
    }

    void manualResetStable(){
        SharedPreferences sp=getSharedPreferences(StableAnalysisService.PREF,MODE_PRIVATE);

        if(sp.getBoolean("running",false)){
            Toast.makeText(this,"A análise está rodando. O checkpoint está protegido.",Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
          .setTitle("Zerar análise?")
          .setMessage("Apaga progresso e resultado. O TXT carregado será mantido.")
          .setNegativeButton("Cancelar",null)
          .setPositiveButton("ZERAR",(d,w)->{
              String uri=sp.getString("uri","");
              sp.edit().clear().putString("uri",uri).apply();

              out.setText("");
              clearActions();
              bar.setProgress(0);
              clock.setText("Progresso: 0,0% | Tempo: 00:00 | ETA: --:--");
              status.setText("Pronto para nova análise.");

              boolean enable=!uri.isEmpty();
              m1.setEnabled(enable);m2.setEnabled(enable);m3.setEnabled(enable);
              m8.setEnabled(enable);m9.setEnabled(enable);m10.setEnabled(enable);
              failTableBtn.setEnabled(enable);
          })
          .show();
    }

    void runModule3(){
        startStableService(0);
    }

    void addUniversePdfButton(CoreEngine.UniverseResult rr){
        Button b=bt("GERAR PDF — "+rr.repeats+" REPETIDAS");
        actions.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));
        b.setOnClickListener(v->pdfUniverse(rr));
    }

    void pdfUniverse(CoreEngine.UniverseResult rr){
        try{
            PdfDocument doc=new PdfDocument();
            PdfDocument.Page pg=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
            Canvas c=pg.getCanvas();Paint p=new Paint(1);
            p.setColor(purple);c.drawRect(0,0,595,82,p);p.setColor(Color.WHITE);p.setTextSize(17);p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("MÓDULO 3 — "+rr.repeats+" REPETIDAS",22,34,p);
            p.setTextSize(11);c.drawText("10 FALHAS EM VERMELHO | 15 DO JOGO EM BRANCO",22,58,p);
            drawVolante(c,rr.failure10,110);doc.finishPage(pg);

            ArrayList<String>ls=wrap(rr.report,80);int at=0,pn=2;
            while(at<ls.size()){
                PdfDocument.Page q=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pn++).create());
                Canvas ca=q.getCanvas();Paint pa=new Paint(1);
                pa.setColor(purple);ca.drawRect(0,0,595,72,pa);pa.setColor(Color.WHITE);pa.setTypeface(Typeface.DEFAULT_BOLD);pa.setTextSize(16);
                ca.drawText("ANÁLISE DO UNIVERSO "+rr.repeats+" REPETIDAS",22,38,pa);
                pa.setColor(Color.DKGRAY);pa.setTypeface(Typeface.DEFAULT);pa.setTextSize(9);int y=96;
                while(at<ls.size()&&y<810){ca.drawText(ls.get(at++),22,y,pa);y+=13;}
                doc.finishPage(q);
            }

            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,"M3_"+rr.repeats+"_REPETIDAS_"+System.currentTimeMillis()+".pdf");
            v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");
            v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/DUPLAS_FALHA");
            Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            OutputStream os=getContentResolver().openOutputStream(u);doc.writeTo(os);os.close();doc.close();
            Toast.makeText(this,"PDF "+rr.repeats+" repetidas salvo.",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Erro PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    void startProgress(){startedAt=SystemClock.elapsedRealtime();pct=0;running=true;bar.setProgress(0);heartbeat();}
    void update(double p,String s){
        pct=Math.max(0,Math.min(100,p));bar.setProgress((int)Math.round(pct*10));
        status.setText(s+" — "+String.format(Locale.US,"%.1f",pct).replace('.',',')+"%");updateClock();
    }
    void heartbeat(){handler.post(new Runnable(){public void run(){if(!running)return;updateClock();handler.postDelayed(this,1000);}});}
    void updateClock(){
        long e=startedAt==0?0:SystemClock.elapsedRealtime()-startedAt;String eta="--:--";
        if(pct>.1&&pct<100){long total=(long)(e*(100.0/pct));eta=fmtTime(Math.max(0,total-e));}
        else if(pct>=100)eta="00:00";
        clock.setText("Progresso: "+String.format(Locale.US,"%.1f",pct).replace('.',',')+"% | Tempo: "+fmtTime(e)+" | ETA: "+eta);
    }
    String fmtTime(long ms){long s=Math.max(0,ms/1000),m=s/60;s%=60;return String.format(Locale.US,"%02d:%02d",m,s);}

    void addPdfButton(CoreEngine.ModuleResult rr){
        Button b=bt("GERAR PDF — 10 FALHAS VERMELHAS + 15 EM BRANCO");
        actions.addView(b,new LinearLayout.LayoutParams(-1,dp(60)));
        b.setOnClickListener(v->pdf(rr));
    }

    void drawVolante(Canvas c,int fail,int top){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int cell=92,left=66;p.setTextAlign(Paint.Align.CENTER);
        for(int n=1;n<=25;n++){
            int ro=(n-1)/5,co=(n-1)%5;float x=left+co*cell,y=top+ro*cell;boolean f=(fail&(1<<(n-1)))!=0;
            p.setStyle(Paint.Style.FILL);p.setColor(f?Color.rgb(220,40,40):Color.WHITE);c.drawRect(x,y,x+70,y+70,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.DKGRAY);c.drawRect(x,y,x+70,y+70,p);
            p.setStyle(Paint.Style.FILL);p.setColor(f?Color.WHITE:Color.BLACK);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(20);
            c.drawText(String.format(Locale.US,"%02d",n),x+35,y+43,p);
        }
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(11);p.setColor(Color.DKGRAY);
        c.drawText("VERMELHO = FALHA PROJETADA | BRANCO = JOGO",left,top+5*cell+22,p);
    }

    void pdf(CoreEngine.ModuleResult rr){
        try{
            PdfDocument doc=new PdfDocument();
            PdfDocument.Page pg=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
            Canvas c=pg.getCanvas();Paint p=new Paint(1);
            p.setColor(purple);c.drawRect(0,0,595,82,p);p.setColor(Color.WHITE);p.setTextSize(17);p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("FALHA DO UNIVERSO 8 / 9 / 10",22,34,p);
            p.setTextSize(11);c.drawText("10 FALHAS EM VERMELHO | 15 DO JOGO EM BRANCO",22,58,p);
            drawVolante(c,rr.failure10,110);doc.finishPage(pg);

            ArrayList<String>ls=wrap(rr.report,80);int at=0,pn=2;
            while(at<ls.size()){
                PdfDocument.Page q=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pn++).create());
                Canvas ca=q.getCanvas();Paint pa=new Paint(1);
                pa.setColor(purple);ca.drawRect(0,0,595,72,pa);pa.setColor(Color.WHITE);pa.setTypeface(Typeface.DEFAULT_BOLD);pa.setTextSize(16);
                ca.drawText("PADRÕES, TENDÊNCIA E JUSTIFICATIVA",22,38,pa);
                pa.setColor(Color.DKGRAY);pa.setTypeface(Typeface.DEFAULT);pa.setTextSize(9);int y=96;
                while(at<ls.size()&&y<810){ca.drawText(ls.get(at++),22,y,pa);y+=13;}
                doc.finishPage(q);
            }
            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,"DUPLAS_FALHA_V12_"+System.currentTimeMillis()+".pdf");
            v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");
            v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/DUPLAS_FALHA");
            Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            OutputStream os=getContentResolver().openOutputStream(u);doc.writeTo(os);os.close();doc.close();
            Toast.makeText(this,"PDF salvo em Downloads/DUPLAS_FALHA",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Erro PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    ArrayList<String>wrap(String x,int n){
        ArrayList<String>o=new ArrayList<>();
        for(String l:x.split("\\n",-1)){
            String s=l;if(s.isEmpty()){o.add("");continue;}
            while(s.length()>n){int q=s.lastIndexOf(' ',n);if(q<10)q=n;o.add(s.substring(0,q));s=s.substring(q).trim();}
            o.add(s);
        }
        return o;
    }
}
