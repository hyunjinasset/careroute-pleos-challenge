package io.careroute.app;

import android.app.Activity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native, offline-first passenger handoff interface for Pleos Connect. */
public final class MainActivity extends Activity {
    private static final int BG=0xfffffaf5, PANEL=0xffffffff, BORDER=0xffe2e4df;
    private static final int WHITE=0xff202420, MUTED=0xff747a73, TEAL=0xfff37829, AMBER=0xffb95b12;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CareSession session;
    private PleosGateway gateway;
    private SharedPreferences prefs;
    private boolean demo=true, demoPark=false, demoOpen=false, connected=false;
    private String source="시연 데이터", renderKey="";
    private LinearLayout root;
    private boolean compact;
    private int widthDp;
    private String completedAt="";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (demo) refreshDemo();
            render(false); handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs=getSharedPreferences("care_session", MODE_PRIVATE);
        session=new CareSession();
        demo=!"live".equals(getIntent().getStringExtra("mode"));
        if (getIntent().getBooleanExtra("reset", false)) prefs.edit().clear().apply();
        session.restore(prefs.getBoolean("opened",false),prefs.getBoolean("check0",false),
                prefs.getBoolean("check1",false),prefs.getBoolean("check2",false),prefs.getBoolean("done",false));
        completedAt=prefs.getString("completedAt","");
        demoPark=prefs.getBoolean("demoPark",false);demoOpen=prefs.getBoolean("demoOpen",false);
        startSource();handler.post(ticker);render(true);
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config);render(true); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);if(gateway!=null)gateway.stop();super.onDestroy(); }
    private long now() { return SystemClock.elapsedRealtime(); }
    private void refreshDemo() {
        session.setRestricted(!demoPark);session.observeGear(demoPark,now());session.observeDoor(demoOpen,now());
    }
    private void startSource() {
        if(gateway!=null){gateway.stop();gateway=null;}
        session.unknownDoor();session.unknownGear();session.setRestricted(true);connected=false;
        if(demo){source="시연 데이터";refreshDemo();}
        else {
            source="차량 데이터 연결 중";
            gateway=new PleosGateway(this,new PleosGateway.Observer(){
                @Override public void gear(boolean park){session.observeGear(park,now());render(false);}
                @Override public void door(boolean open){session.observeDoor(open,now());persist();render(false);}
                @Override public void restricted(boolean v){session.setRestricted(v);render(false);}
                @Override public void status(String s,boolean ok){source=s;connected=ok;render(false);}
                @Override public void doorUnavailable(){session.unknownDoor();render(false);}
                @Override public void gearUnavailable(){session.unknownGear();render(false);}
            });gateway.start();
        }
    }
    private void persist(){
        prefs.edit().putBoolean("opened",session.wasDoorOpened()).putBoolean("check0",session.isConfirmed(0))
                .putBoolean("check1",session.isConfirmed(1)).putBoolean("check2",session.isConfirmed(2))
                .putBoolean("done",session.isFinished()).putBoolean("demoPark",demoPark)
                .putBoolean("demoOpen",demoOpen).putString("completedAt",completedAt).apply();
    }
    private void render(boolean force){
        if(session==null)return;
        CareSession.Stage stage=session.stage(now());
        String key=stage+source+demo+demoPark+demoOpen+session.isConfirmed(0)+session.isConfirmed(1)+session.isConfirmed(2)+session.isFinished();
        if(!force&&key.equals(renderKey))return;renderKey=key;
        widthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        int heightDp=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);
        compact=heightDp<650;
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(28),dp(compact?12:16),dp(28),dp(12));
        root.addView(header(),new LinearLayout.LayoutParams(-1,dp(64)));
        root.addView(progress(stage),new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout body=row();body.setPadding(0,dp(12),0,dp(12));
        if(widthDp>=860){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(widthDp>1400?420:widthDp>1100?340:280),-1);lp.setMargins(0,0,dp(30),0);body.addView(journey(stage),lp);}
        LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(0,-1,1);body.addView(main(stage),center);

        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(footer(),new LinearLayout.LayoutParams(-1,dp(demo?56:42)));
        setContentView(root);
    }
    private View header(){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(new BrandMark(),new LinearLayout.LayoutParams(dp(49),dp(49)));
        LinearLayout name=row();name.setGravity(Gravity.CENTER_VERTICAL);name.setPadding(dp(12),0,0,0);
        TextView brandName=text("케어롯",30,WHITE,true);brandName.setLetterSpacing(.015f);name.addView(brandName);TextView en=text("CareRoute",15,MUTED,true);en.setPadding(dp(12),dp(6),0,0);name.addView(en);
        line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("●  "+source,13,demo?AMBER:connected?0xff307050:MUTED,true);
        badge.setPadding(dp(14),dp(9),dp(14),dp(9));badge.setBackground(bg(demo?0xffffeee1:0xffeaf2e9,0,20));line.addView(badge);
        if(session.stage(now())!=CareSession.Stage.MOVING || demo){
            TextView settings=text("연동 설정",14,MUTED,false);settings.setGravity(Gravity.CENTER);settings.setMinHeight(dp(54));settings.setPadding(dp(18),0,0,0);settings.setOnClickListener(v->settings());line.addView(settings);
        }
        return line;
    }
    private View progress(CareSession.Stage stage){
        LinearLayout out=row();out.setGravity(Gravity.CENTER_VERTICAL);out.setPadding(0,dp(6),0,0);
        int active=switch(stage){case UNKNOWN,MOVING->0;case ARRIVED->1;case EXITING,HANDOFF_PENDING->2;case COMPLETE->3;};
        String[] titles={"이동","정차","하차 확인","인계 완료"};
        for(int i=0;i<4;i++){
            LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);
            TextView number=text(i<active?"✓":String.valueOf(i+1),12,i<=active?0xffffffff:MUTED,true);
            number.setGravity(Gravity.CENTER);number.setBackground(bg(i==active?TEAL:i<active?WHITE:0xffe8eae4,0,20));item.addView(number,new LinearLayout.LayoutParams(dp(23),dp(23)));
            TextView label=text(titles[i],14,i==active?TEAL:i<active?WHITE:MUTED,i==active);label.setPadding(dp(8),0,0,0);item.addView(label);
            out.addView(item,new LinearLayout.LayoutParams(0,-2,1));
            if(i<3){View connector=new View(this);connector.setBackgroundColor(BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(28),dp(1));lp.setMargins(0,0,dp(18),0);out.addView(connector,lp);}
        }
        return out;
    }
    private View journey(CareSession.Stage stage){
        LinearLayout panel=column();panel.setPadding(dp(20),dp(19),dp(20),dp(18));panel.setBackground(bg(PANEL,0xfff0e8de,24));
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("오늘의 동행",18,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        top.addView(text("샘플 운행",11,MUTED,false));panel.addView(top);space(panel,15);
        JourneyDrawing map=new JourneyDrawing();map.setBackground(bg(0xfff0f1e9,0,18));map.setClipToOutline(true);
        panel.addView(map,new LinearLayout.LayoutParams(-1,0,1));space(panel,17);
        panel.addView(text("오늘의 도착지",11,MUTED,false));space(panel,4);panel.addView(text("새봄 복지관",24,WHITE,true));space(panel,5);panel.addView(text("정문 승하차 구역",13,MUTED,false));space(panel,16);
        View divider=new View(this);divider.setBackgroundColor(0xffefe8df);panel.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));space(panel,13);
        LinearLayout passenger=row();passenger.setGravity(Gravity.CENTER_VERTICAL);
        passenger.addView(picture(R.drawable.care_passenger),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout details=column();details.setPadding(dp(10),0,0,0);details.addView(text("승객 A · 휠체어 동행",14,WHITE,true));space(details,4);details.addView(text("보호자에게 직접 인계",12,MUTED,false));passenger.addView(details,new LinearLayout.LayoutParams(0,-2,1));panel.addView(passenger);space(panel,15);
        LinearLayout tiles=row();
        LinearLayout.LayoutParams tileLeft=new LinearLayout.LayoutParams(0,dp(49),1);tileLeft.setMargins(0,0,dp(9),0);
        tiles.addView(statusTile(R.drawable.care_door,session.isDoorOpen()?"뒷문 열림":"뒷문 닫힘"),tileLeft);
        tiles.addView(statusTile(R.drawable.care_wheel,"휠체어 동행"),new LinearLayout.LayoutParams(0,dp(49),1));panel.addView(tiles);
        return panel;
    }
    private View statusTile(int resource,String label){
        LinearLayout tile=row();tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(10),0,dp(7),0);tile.setBackground(bg(0xfffff2e5,0,15));
        tile.addView(picture(resource),new LinearLayout.LayoutParams(dp(30),dp(30)));
        TextView value=text(label,12,WHITE,true);value.setPadding(dp(6),0,0,0);tile.addView(value,new LinearLayout.LayoutParams(0,-2,1));return tile;
    }
    private View main(CareSession.Stage stage){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=column();content.setPadding(dp(4),0,dp(4),0);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        boolean pending=stage==CareSession.Stage.HANDOFF_PENDING;int accent=TEAL;
        String eyebrow=switch(stage){case UNKNOWN->"연결 확인";case MOVING->"이동 중";case ARRIVED->"P 기어 · 정차 확인";case EXITING->"오른쪽 뒷문 · 열림";case HANDOFF_PENDING->"보호자 인계 확인 대기";case COMPLETE->"운전자 확인 기록 완료";};
        TextView above=text(eyebrow,14,accent,true);above.setLetterSpacing(.01f);content.addView(above);space(content,10);
        String title=switch(stage){case UNKNOWN->"차량 상태를\n확인하고 있어요.";case MOVING->"오늘의 동행,\n마지막까지 함께.";case ARRIVED->"도착했어요.\n하차를 준비해요.";case EXITING->"내리는 순간도\n함께 확인해요.";case HANDOFF_PENDING->"문은 닫혔지만,\n인계는 아직이에요.";case COMPLETE->"인계까지,\n잘 마쳤어요.";};
        TextView headline=text(title,compact?29:34,WHITE,true);headline.setLetterSpacing(.005f);headline.setLineSpacing(dp(9),1.06f);if(pending){SpannableString marked=new SpannableString(title);int line=title.indexOf("\n");if(line>=0)marked.setSpan(new ForegroundColorSpan(TEAL),line+1,title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);headline.setText(marked);}content.addView(headline);space(content,10);
        String subtitle=switch(stage){case UNKNOWN->"연결이 확인될 때까지 확인 입력을 잠시 멈춥니다.";case MOVING->"주행 중에는 확인 입력을 잠시 멈춥니다.";case ARRIVED->"문이 열린 뒤 승객의 하차를 직접 확인해 주세요.";case EXITING->"하차와 인계는 동행자가 직접 확인해 주세요.";case HANDOFF_PENDING->"문 닫힘과 인계 완료는 서로 다른 확인입니다.";case COMPLETE->"승객·보조기구·보호자 인계를 직접 확인했습니다.";};
        TextView sub=text(subtitle,compact?15:16,MUTED,false);sub.setLineSpacing(dp(3),1);content.addView(sub);space(content,compact?16:20);
        if(stage==CareSession.Stage.MOVING||stage==CareSession.Stage.UNKNOWN){
            LinearLayout quiet=column();quiet.setPadding(dp(22),dp(18),dp(22),dp(18));quiet.setBackground(bg(PANEL,0,22));
            quiet.addView(new VanDrawing(false),new LinearLayout.LayoutParams(-1,dp(compact?84:110)));space(quiet,10);
            quiet.addView(text("도착지  새봄 복지관 정문",18,WHITE,true));space(quiet,7);
            quiet.addView(text("동행 메모  오른쪽 뒷문 · 보호자 직접 인계",14,MUTED,false));content.addView(quiet);
        }else if(stage==CareSession.Stage.COMPLETE){
            LinearLayout receipt=column();receipt.setPadding(dp(22),dp(20),dp(22),dp(20));receipt.setBackground(bg(0xffffeee1,0,22));
            LinearLayout receiptTitle=row();receiptTitle.setGravity(Gravity.CENTER_VERTICAL);receiptTitle.addView(picture(R.drawable.care_done),new LinearLayout.LayoutParams(dp(42),dp(42)));TextView receiptLabel=text("인계 확인을 마쳤습니다",22,WHITE,true);receiptLabel.setPadding(dp(10),0,0,0);receiptTitle.addView(receiptLabel);receipt.addView(receiptTitle);space(receipt,14);
            receipt.addView(text("보조기구·짐  확인\n승객 하차  확인\n보호자 인계  확인",17,WHITE,false));space(receipt,14);
            receipt.addView(text("확인 시각  "+completedAt,13,MUTED,false));content.addView(receipt);space(content,16);
            content.addView(button("확인 기록 보기",false,v->showRecord()),new LinearLayout.LayoutParams(-1,dp(58)));
        }else{
            String[] labels={"보조기구와 짐을 확인했어요","승객의 하차를 직접 확인했어요","보호자에게 직접 인계했어요"};
            String[] details={"휠체어 · 소지품 전달","승객이 차량 밖에 있는지 직접 확인","하차 확인 후 진행할 수 있어요"};
            for(int i=0;i<3;i++){
                final int index=i;boolean checked=session.isConfirmed(i);
                boolean enabled=session.canInteract(now())&&session.wasDoorOpened()&&(i<2||session.isConfirmed(0)&&session.isConfirmed(1));
                LinearLayout check=row();check.setGravity(Gravity.CENTER_VERTICAL);check.setPadding(dp(17),dp(9),dp(16),dp(9));
                check.setBackground(bg(checked?0xfffff8f1:PANEL,checked?0xfff3dac6:i==2?0xffeaa466:BORDER,17));
                check.addView(taskArtwork(i,checked),new LinearLayout.LayoutParams(dp(46),dp(46)));
                LinearLayout words=column();words.setPadding(dp(12),0,0,0);words.addView(text(labels[i],compact?16:17,enabled||checked?WHITE:MUTED,true));
                if(!compact){space(words,3);words.addView(text(checked?"운전자 직접 확인":i==2&&enabled?"직접 인계한 뒤 눌러 주세요":details[i],12,MUTED,false));}
                check.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                check.setContentDescription(labels[i]+(checked?", 확인됨":", 미확인"));check.setEnabled(enabled);check.setAlpha(enabled||checked?1f:.60f);
                check.setOnClickListener(v->{if(session.confirm(index,now())){persist();render(true);}});
                check.setFocusable(true);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(compact?65:75));lp.setMargins(0,0,0,dp(8));content.addView(check,lp);
            }
            space(content,4);
            Button finish=button("인계 확인하고 이동 마치기",true,v->{if(session.finish(now())){completedAt=new SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date());persist();render(true);}});
            finish.setEnabled(session.canFinish(now()));finish.setTextColor(finish.isEnabled()?0xffffffff:0xffb6a18b);finish.setBackground(bg(finish.isEnabled()?TEAL:0xfff1e7db,0,17));content.addView(finish,new LinearLayout.LayoutParams(-1,dp(compact?56:60)));
            space(content,8);content.addView(text("확인 입력은 정차 중에만 · 실제 상태는 동행자가 직접 확인",11,MUTED,false));
        }
        return scroll;
    }
    private View contextCard(CareSession.Stage stage){
        LinearLayout col=column();col.setPadding(dp(20),dp(22),dp(20),dp(18));col.setBackground(bg(PANEL,BORDER,22));
        col.addView(text("이번 승객의 동행 정보",16,WHITE,true));space(col,16);
        col.addView(new VanDrawing(session.isDoorOpen()),new LinearLayout.LayoutParams(-1,dp(compact?100:140)));space(col,12);
        info(col,"승객 A","가상 시나리오");info(col,"휠체어 동행","보조기구·짐 확인");info(col,"오른쪽 뒷문","현재 "+(session.isFresh(now())?(session.isDoorOpen()?"열림":"닫힘"):"확인 중"));
        col.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        TextView note=text("차량 상태와 직접 확인을\n한 흐름으로 이어갑니다.",13,MUTED,false);note.setLineSpacing(dp(4),1);col.addView(note);
        return col;
    }
    private View footer(){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        if(demo){
            TextView label=text("시연 조작",12,AMBER,true);label.setPadding(0,0,dp(12),0);line.addView(label);
            demoButton(line,"주행",()->{demoPark=false;demoOpen=false;});
            demoButton(line,"P 정차",()->{demoPark=true;});
            demoButton(line,"문 열기",()->{demoOpen=true;});
            demoButton(line,"문 닫기",()->{demoOpen=false;});
            demoButton(line,"처음부터",()->{demoPark=false;demoOpen=false;session.reset();completedAt="";});
            TextView note=text("가상 승객 · 차량 신호 시뮬레이션",11,MUTED,false);note.setGravity(Gravity.END);line.addView(note,new LinearLayout.LayoutParams(0,-2,1));
        }else{
            String state=session.isFresh(now())?(session.isParked()?"P · 정차":"주행 기어")+"   /   오른쪽 뒷문 "+(session.isDoorOpen()?"열림":"닫힘"):"차량 데이터 대기";
            line.addView(text("●  "+state,13,connected?TEAL:MUTED,true));
            TextView note=text("샘플 승객 정보 · 이 기기에만 확인 기록 보관",11,MUTED,false);note.setGravity(Gravity.END);line.addView(note,new LinearLayout.LayoutParams(0,-2,1));
        }
        return line;
    }
    private void demoButton(LinearLayout line,String label,Runnable action){
        Button b=button(label,false,v->{action.run();refreshDemo();persist();render(true);});b.setTextSize(13);b.setPadding(dp(11),0,dp(11),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(91),dp(48));lp.setMargins(0,0,dp(8),0);line.addView(b,lp);
    }
    private void settings(){
        if(!demo&&session.stage(now())==CareSession.Stage.MOVING)return;
        new AlertDialog.Builder(this).setTitle("차량 데이터 연결")
                .setItems(new String[]{"Pleos 차량 SDK로 연결","시연 데이터로 체험","확인 기록 보기","새 확인 기록 시작"},(dialog,which)->{
                    if(which==0||which==1){demo=which==1;session=new CareSession();completedAt="";demoPark=false;demoOpen=false;persist();startSource();render(true);}
                    if(which==2)showRecord();
                    if(which==3){session.reset();completedAt="";persist();render(true);}
                }).setNegativeButton("닫기",null).show();
    }
    private void showRecord(){
        if(!session.canInteract(now()))return;
        List<String> events=session.getEvents();String body=events.isEmpty()?"이 실행에서 아직 기록된 이벤트가 없습니다.":String.join("\n\n",events);
        new AlertDialog.Builder(this).setTitle("이번 이동 확인 기록").setMessage(body+"\n\n확인 정보는 이 기기에만 저장되며 외부로 전송되지 않습니다.\n새 확인 기록을 시작하면 이전 확인을 지웁니다.").setPositiveButton("닫기",null).show();
    }
    private void info(LinearLayout parent,String label,String value){
        parent.addView(text(label,15,WHITE,true));space(parent,5);parent.addView(text(value,12,MUTED,false));space(parent,16);
    }
    private TextView text(String value,float size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setLineSpacing(dp(3),1);return t;
    }
    private Button button(String label,boolean primary,View.OnClickListener click){
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setTextColor(primary?0xffffffff:WHITE);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));b.setBackground(bg(primary?WHITE:PANEL,primary?0:BORDER,16));
        b.setStateListAnimator(null);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(click);return b;
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void space(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}

    private ImageView picture(int resource){
        ImageView view=new ImageView(this);view.setImageResource(resource);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;
    }
    private View taskArtwork(int index,boolean checked){
        FrameLayout art=new FrameLayout(this);int[] resources={R.drawable.care_bag,R.drawable.care_passenger,R.drawable.care_guardian};
        art.addView(picture(resources[index]),new FrameLayout.LayoutParams(dp(43),dp(43),Gravity.TOP|Gravity.START));
        TextView badge=text(checked?"✓":String.valueOf(index+1),10,checked?0xffffffff:0xffa2a596,true);badge.setGravity(Gravity.CENTER);badge.setBackground(bg(checked?0xff78a67e:0xffe8e9df,0xffffffff,20));
        art.addView(badge,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.BOTTOM|Gravity.END));return art;
    }
    private final class BrandMark extends ImageView {
        BrandMark(){super(MainActivity.this);setImageResource(R.drawable.care_logo);setScaleType(ImageView.ScaleType.FIT_CENTER);setContentDescription("케어롯 캐럿 하트 로고");}
    }
    private final class JourneyDrawing extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final Path path=new Path();
        JourneyDrawing(){super(MainActivity.this);setContentDescription("가상 운행 출발지에서 새봄 복지관으로 이동하는 경로 예시");}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();c.save();c.scale(w/280f,h/230f);
            p.setStyle(Paint.Style.FILL);p.setColor(0xffe5e9dc);
            for(int row=0;row<3;row++)for(int col=0;col<3;col++){float x=col*112-20,y=row*95-27;c.drawRoundRect(x,y,x+78,y+57,12,12,p);}
            p.setColor(0xffffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(17);p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(-20,72,300,72,p);c.drawLine(82,-20,82,260,p);c.drawLine(211,-20,211,260,p);c.drawLine(-20,169,300,169,p);
            p.setColor(0xffd8ded0);p.setStrokeWidth(1);c.drawLine(-20,72,300,72,p);c.drawLine(82,-20,82,260,p);c.drawLine(211,-20,211,260,p);c.drawLine(-20,169,300,169,p);
            p.setColor(TEAL);p.setStrokeWidth(7);path.reset();path.moveTo(55,169);path.lineTo(163,169);path.quadTo(211,169,211,121);path.lineTo(211,72);c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xffffffff);c.drawCircle(55,169,10,p);p.setColor(WHITE);c.drawCircle(55,169,5,p);
            p.setColor(TEAL);c.drawCircle(211,65,18,p);path.reset();path.moveTo(200,75);path.lineTo(211,91);path.lineTo(222,75);path.close();c.drawPath(path,p);p.setColor(0xffffffff);c.drawCircle(211,64,6,p);
            p.setColor(WHITE);c.drawRoundRect(106,145,150,170,8,8,p);p.setColor(0xffffffff);c.drawRoundRect(115,150,140,158,3,3,p);p.setColor(WHITE);c.drawCircle(116,171,5,p);c.drawCircle(140,171,5,p);
            p.setColor(0xff747a73);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(11);c.drawText("예시 경로",15,23,p);
            c.restore();
        }
    }
    private final class VanDrawing extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final Path path=new Path();private final boolean open;
        VanDrawing(boolean open){super(MainActivity.this);this.open=open;setContentDescription("동행 차량 도어 상태 그림, 오른쪽 뒷문 "+(open?"열림":"닫힘"));}
        @Override protected void onDraw(Canvas c){float scale=Math.min(getWidth()/240f,getHeight()/130f);c.save();c.translate((getWidth()-240*scale)/2,(getHeight()-130*scale)/2);c.scale(scale,scale);
            p.setColor(0xfff6ede1);p.setStyle(Paint.Style.FILL);c.drawRoundRect(19,37,220,104,20,20,p);p.setColor(0xffb5b9ad);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(19,37,220,104,20,20,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xffc9dcd7);c.drawRoundRect(38,46,83,69,5,5,p);c.drawRoundRect(93,46,137,69,5,5,p);c.drawRoundRect(147,46,194,69,5,5,p);
            p.setColor(open?TEAL:0xffedd5bc);c.drawRoundRect(open?125:91,75,open?178:143,106,4,4,p);p.setColor(BG);c.drawCircle(60,105,14,p);c.drawCircle(181,105,14,p);p.setColor(MUTED);c.drawCircle(60,105,6,p);c.drawCircle(181,105,6,p);
            if(open){p.setColor(TEAL);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);Path ramp=path;ramp.reset();ramp.moveTo(100,106);ramp.lineTo(81,125);ramp.lineTo(125,125);ramp.lineTo(144,106);c.drawPath(ramp,p);}
            c.restore();}
    }
}
