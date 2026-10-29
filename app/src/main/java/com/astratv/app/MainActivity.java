package com.astratv.app;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.json.*;
import java.io.*;
import java.util.*;

/** Native TV shell. Every card leads to native details, episodes and source selection. */
@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
    private TvUi ui;
    private ProviderRepository repository;
    private android.content.SharedPreferences prefs;
    private FrameLayout frame;
    private LinearLayout content,body;
    private ScrollView scroll;
    private ImageView backdrop;
    private TextView heroTitle,heroMeta,heroDescription,heroProvider;
    private Library.Title featured,detailTitle;
    private String screen="Home",filter="All",focusKey="";
    private int generation=0,homeY=0;
    private boolean heroChosen=false;
    private final Map<String,ProviderRepository.Catalog> catalogs=new LinkedHashMap<>();
    private final Map<String,String> states=new HashMap<>();
    private final Map<String,LinearLayout> panels=new HashMap<>();
    private final Set<String> loading=new HashSet<>();
    private final Set<String> enriching=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new TvUi(this);prefs=getSharedPreferences("astra",0);repository=new ProviderRepository(this);migrateLegacy();
        for(Library.Addon addon:Library.addons()){
            List<Library.Title> cached=repository.cached(addon.id);
            if(!cached.isEmpty())catalogs.put(addon.id,new ProviderRepository.Catalog(cached,"Saved catalog",true));
        }
        showHome();refreshAll();
    }
    private void shell(String name,boolean cinematic){
        generation++;screen=name;panels.clear();heroTitle=null;heroChosen=false;
        frame=new FrameLayout(this);frame.setBackgroundColor(TvUi.BG);
        if(cinematic){
            backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);FrameLayout.LayoutParams image=new FrameLayout.LayoutParams(-1,ui.dp(410),Gravity.TOP);image.leftMargin=ui.dp(160);frame.addView(backdrop,image);
            View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{TvUi.BG,0xf0080b12,0x33080b12}));frame.addView(shade,new FrameLayout.LayoutParams(-1,ui.dp(420)));
            View fade=new View(this);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00080b12,0x55080b12,TvUi.BG}));frame.addView(fade,new FrameLayout.LayoutParams(-1,ui.dp(420)));
        }else backdrop=null;
        LinearLayout layout=new LinearLayout(this);layout.setClipChildren(false);frame.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout rail=ui.column();rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(ui.dp(9),ui.dp(22),ui.dp(9),ui.dp(16));rail.setBackgroundColor(0xe6080b12);
        TextView brand=ui.bold("✦",29);brand.setGravity(Gravity.CENTER);rail.addView(brand,new LinearLayout.LayoutParams(-1,ui.dp(62)));ui.gap(rail,20);
        String[] labels={"Home","Search","Watchlist","Add-ons","Settings"};String[] icons={"⌂","⌕","♡","⊞","⚙"};
        for(int i=0;i<labels.length;i++){
            String target=labels[i];LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(0,ui.dp(9),0,ui.dp(9));
            TextView icon=ui.text(icons[i],23,TvUi.WHITE);icon.setGravity(Gravity.CENTER);item.addView(icon);ui.gap(item,4);TextView label=ui.text(target,9,TvUi.MUTED);item.addView(label);
            ui.focusCard(item,null);if(target.equals(name))item.setBackground(ui.shape(0xff252c39,0,12));item.setContentDescription(target);item.setOnClickListener(v->navigate(target));rail.addView(item,new LinearLayout.LayoutParams(-1,ui.dp(62)));ui.gap(rail,4);
        }
        layout.addView(rail,new LinearLayout.LayoutParams(ui.dp(76),-1));
        content=ui.column();content.setPadding(ui.dp(25),ui.dp(22),ui.dp(30),0);layout.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView wordmark=ui.bold("ASTRA",17);wordmark.setLetterSpacing(.24f);header.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));TextView profile=ui.text("●   YOUR SPACE",10,TvUi.MUTED);header.addView(profile);content.addView(header,new LinearLayout.LayoutParams(-1,ui.dp(28)));
        scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setVerticalScrollBarEnabled(false);body=ui.column();body.setPadding(ui.dp(5),ui.dp(8),ui.dp(6),ui.dp(30));scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(frame);
    }
    private void navigate(String destination){
        if(screen.equals("Home"))homeY=scroll.getScrollY();
        switch(destination){case "Search":showSearch();break;case "Watchlist":showWatchlist();break;case "Add-ons":showAddons();break;case "Settings":showSettings();break;default:showHome();}
    }
    private void showHome(){
        shell("Home",true);LinearLayout tabs=new LinearLayout(this);
        for(String value:new String[]{"All","Movies","Shows","Live"}){TextView tab=ui.action(value,value.equals(filter),()->{filter=value;homeY=0;showHome();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,ui.dp(38));p.setMargins(0,0,ui.dp(10),0);tabs.addView(tab,p);}body.addView(tabs);ui.gap(body,18);
        LinearLayout hero=ui.column();hero.setGravity(Gravity.CENTER_VERTICAL);heroProvider=ui.text("YOUR CINEMA, CONNECTED",10,TvUi.ACCENT);heroProvider.setLetterSpacing(.2f);hero.addView(heroProvider);ui.gap(hero,10);
        heroTitle=ui.bold("Your next great watch.",compact()?32:38);heroTitle.setMaxLines(compact()?1:2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);hero.addView(heroTitle);ui.gap(hero,10);heroMeta=ui.text("Movies  ·  Shows  ·  Live TV",13,TvUi.WHITE);hero.addView(heroMeta);ui.gap(hero,10);
        heroDescription=ui.text("Browse your sources, find your next watch, and play right here.",13,TvUi.MUTED);heroDescription.setMaxLines(compact()?1:2);hero.addView(heroDescription,new LinearLayout.LayoutParams(ui.dp(430),-2));ui.gap(hero,16);
        LinearLayout heroActions=new LinearLayout(this);heroActions.addView(ui.action("Explore title",true,()->{if(featured!=null)showDetails(featured);else showAddons();}));hero.addView(heroActions);body.addView(hero,new LinearLayout.LayoutParams(-1,ui.dp(compact()?180:262)));
        List<Library.Title> continuing=new ArrayList<>();for(Library.Title t:allTitles())if(position(t)>0)continuing.add(t);if(!continuing.isEmpty())shelf(body,"Continue watching",continuing,false);
        for(Library.Addon a:Library.addons()){
            if(!repository.enabled(a.id)||!categoryMatches(a.id))continue;
            LinearLayout panel=ui.column();panels.put(a.id,panel);body.addView(panel);updatePanel(a);
        }
        List<Library.Title> library=Library.titles(this);if(!library.isEmpty())shelf(body,"Your library",library,false);
        shelf(body,"Open cinema",Library.samples(),true);
        if(featured!=null)setHero(featured);
        scroll.post(()->{scroll.scrollTo(0,homeY);if(!focusKey.isEmpty()){View found=findTagged(body,focusKey);if(found!=null)found.requestFocus();}});
    }
    private boolean categoryMatches(String id){return filter.equals("All")||filter.equals("Movies")&&(id.equals("movierulz")||id.equals("netmirror"))||filter.equals("Shows")&&(id.equals("tamil")||id.equals("telugu")||id.equals("netmirror"))||filter.equals("Live")&&id.equals("news");}
    private void setHero(Library.Title title){
        if(heroTitle==null)return;featured=title;heroTitle.setText(title.name);heroProvider.setText(title.providerName.toUpperCase(Locale.ROOT));
        heroMeta.setText(join(title.year,title.genre,title.rating.isEmpty()?"":"★ "+title.rating));
        heroDescription.setText(title.description.isEmpty()?"Discover details and available sources for this title.":title.description);
        Artwork.load(backdrop,title.background.isEmpty()?title.poster:title.background);
    }
    private void updatePanel(Library.Addon addon){
        LinearLayout panel=panels.get(addon.id);if(panel==null)return;panel.removeAllViews();ProviderRepository.Catalog cat=catalogs.get(addon.id);
        if(cat!=null&&!cat.titles.isEmpty()){
            shelf(panel,addon.name,cat.titles,!addon.id.equals("movierulz")&&!addon.id.equals("netmirror"));
            if(cat.stale){TextView stale=ui.text(cat.status,11,TvUi.MUTED);stale.setMaxLines(2);panel.addView(stale);ui.gap(panel,10);}
            if(addon.id.equals("movierulz")&&!heroChosen&&heroTitle!=null){heroChosen=true;setHero(cat.titles.get(0));}
        }else{
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.addView(ui.bold(addon.name,20),new LinearLayout.LayoutParams(0,-2,1));
            TextView connect=ui.action(addon.id.equals("netmirror")?"Connect":"Retry",false,()->{if(addon.id.equals("netmirror"))configure(addon);else load(addon,1);});line.addView(connect);panel.addView(line);
            TextView state=ui.text(states.getOrDefault(addon.id,"Loading catalog…"),12,TvUi.MUTED);state.setMaxLines(2);panel.addView(state);ui.gap(panel,22);
        }
    }
    private void shelf(LinearLayout parent,String name,List<Library.Title> titles,boolean wide){
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(ui.bold(name,20),new LinearLayout.LayoutParams(0,-2,1));
        Library.Addon addon=addonFor(titles.isEmpty()?"":titles.get(0).provider);if(addon!=null)heading.addView(ui.action("View all",false,()->showProvider(addon,1)));parent.addView(heading);ui.gap(parent,8);
        boolean landscape=wide||compact();RecyclerView row=new RecyclerView(this);row.setHorizontalScrollBarEnabled(false);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(ui.dp(4),ui.dp(6),ui.dp(8),ui.dp(8));
        LinearLayoutManager layout=new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false);row.setLayoutManager(layout);row.setItemAnimator(null);row.setNestedScrollingEnabled(false);
        row.setAdapter(new ShelfAdapter(titles,landscape));parent.addView(row,new LinearLayout.LayoutParams(-1,ui.dp(landscape?184:244)));ui.gap(parent,20);
        for(int i=0;i<titles.size();i++)if(titles.get(i).key().equals(focusKey)){layout.scrollToPosition(i);if(screen.equals("Home"))row.post(()->{View found=findTagged(row,focusKey);if(found!=null)found.requestFocus();});break;}
    }
    private final class ShelfAdapter extends RecyclerView.Adapter<ShelfHolder>{
        final List<Library.Title> titles;final boolean wide;
        ShelfAdapter(List<Library.Title> titles,boolean wide){this.titles=titles;this.wide=wide;setHasStableIds(true);}
        @Override public long getItemId(int index){return titles.get(index).key().hashCode();}
        @Override public ShelfHolder onCreateViewHolder(android.view.ViewGroup parent,int type){FrameLayout slot=new FrameLayout(MainActivity.this);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(ui.dp(wide?224:139),-1);p.rightMargin=ui.dp(13);slot.setLayoutParams(p);slot.setClipChildren(false);return new ShelfHolder(slot);}
        @Override public void onBindViewHolder(ShelfHolder holder,int index){FrameLayout slot=(FrameLayout)holder.itemView;slot.removeAllViews();slot.addView(card(titles.get(index),wide),new FrameLayout.LayoutParams(-1,-2));}
        @Override public int getItemCount(){return titles.size();}
    }
    private static final class ShelfHolder extends RecyclerView.ViewHolder{ShelfHolder(View view){super(view);}}
    private LinearLayout.LayoutParams cardParams(boolean wide){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(wide?224:139),-2);p.setMargins(0,0,ui.dp(13),0);return p;}
    private View card(Library.Title t,boolean wide){
        LinearLayout card=ui.column();card.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(6));card.setTag(t.key());card.setContentDescription(t.name+", "+t.providerName);
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(ui.shape(0xff1c2431,0,8));artwork.setClipToOutline(true);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));Artwork.load(image,t.poster);
        if(t.poster.isEmpty()){TextView placeholder=ui.bold(t.name.substring(0,Math.min(2,t.name.length())).toUpperCase(Locale.ROOT),30);placeholder.setGravity(Gravity.CENTER);artwork.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));}
        if(position(t)>0){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgress(20);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,ui.dp(3),Gravity.BOTTOM);artwork.addView(progress,pp);}
        card.addView(artwork,new LinearLayout.LayoutParams(-1,ui.dp(wide?124:184)));ui.gap(card,8);TextView title=ui.bold(t.name,13);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);ui.gap(card,4);TextView subtitle=ui.text(join(t.year,t.type.equals("tv")?"LIVE":t.providerName),10,TvUi.MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);card.addView(subtitle);
        ui.focusCard(card,()->{focusKey=t.key();if(screen.equals("Home"))setHero(t);
            if(t.provider.equals("netmirror")&&t.name.startsWith("NetMirror · ")&&enriching.add(t.key()))repository.io.execute(()->{try{Library.Title full=repository.details(t);runOnUiThread(()->{enriching.remove(t.key());if(isDestroyed())return;title.setText(full.name);persist(full);if(card.hasFocus()&&screen.equals("Home"))setHero(full);});}catch(Exception ignored){runOnUiThread(()->enriching.remove(t.key()));}});
        });card.setOnClickListener(v->{if(screen.equals("Home"))homeY=scroll.getScrollY();showDetails(t);});return card;
    }
    private void refreshAll(){for(Library.Addon addon:Library.addons())if(repository.enabled(addon.id))load(addon,1);}
    private void load(Library.Addon addon,int page){
        if(!loading.add(addon.id))return;states.put(addon.id,"Loading catalog…");
        repository.io.execute(()->{
            ProviderRepository.Catalog result=null;String failure=null;try{result=repository.load(addon,"",page);}catch(Exception e){failure=ProviderRepository.message(e);}
            ProviderRepository.Catalog value=result;String error=failure;
            runOnUiThread(()->{if(isDestroyed())return;loading.remove(addon.id);
                if(value!=null){if(page>1&&catalogs.containsKey(addon.id)){List<Library.Title> merged=new ArrayList<>(catalogs.get(addon.id).titles);Set<String> keys=new HashSet<>();for(Library.Title t:merged)keys.add(t.key());for(Library.Title t:value.titles)if(keys.add(t.key()))merged.add(t);catalogs.put(addon.id,new ProviderRepository.Catalog(merged,value.status,value.stale));}else catalogs.put(addon.id,value);states.put(addon.id,value.status);}else states.put(addon.id,error);
                if(screen.equals("Home"))updatePanel(addon);else if(screen.equals("Provider:"+addon.id))showProvider(addon,value==null?Math.max(1,page-1):page);else if(screen.equals("Add-ons"))showAddons();
            });
        });
    }
    private void showProvider(Library.Addon addon,int page){
        shell("Provider:"+addon.id,false);heading(addon.name,states.getOrDefault(addon.id,"Catalog"));
        ProviderRepository.Catalog cat=catalogs.get(addon.id);if(cat!=null){grid(body,cat.titles);ui.gap(body,16);body.addView(ui.action(loading.contains(addon.id)?"Loading…":"Load more",false,()->load(addon,page+1)));}
        else{body.addView(ui.action("Load catalog",true,()->load(addon,1)));ui.gap(body,10);body.addView(ui.action("Provider settings",false,()->configure(addon)));}
    }
    private void grid(LinearLayout parent,List<Library.Title> titles){
        LinearLayout row=null;int width=getResources().getDisplayMetrics().widthPixels;int cols=Math.max(2,(int)(width/getResources().getDisplayMetrics().density-160)/153);
        for(int i=0;i<titles.size();i++){if(i%cols==0){row=new LinearLayout(this);row.setClipChildren(false);parent.addView(row);ui.gap(parent,14);}row.addView(card(titles.get(i),false),cardParams(false));}
    }
    private void showDetails(Library.Title title){
        detailTitle=title;shell("Details",true);Artwork.load(backdrop,title.background.isEmpty()?title.poster:title.background);
        TextView back=ui.action("‹  Back",false,this::showHome);body.addView(back,new LinearLayout.LayoutParams(-2,ui.dp(38)));ui.gap(body,26);
        TextView provider=ui.text(title.providerName.toUpperCase(Locale.ROOT),11,TvUi.ACCENT);provider.setLetterSpacing(.18f);body.addView(provider);ui.gap(body,12);TextView name=ui.bold(title.name,34);body.addView(name);ui.gap(body,12);TextView metadata=ui.text(join(title.year,title.genre,title.rating.isEmpty()?"":"★ "+title.rating),14,TvUi.WHITE);body.addView(metadata);ui.gap(body,16);
        TextView description=ui.text(title.description.isEmpty()?"Loading title details…":title.description,15,TvUi.MUTED);description.setMaxWidth(ui.dp(650));body.addView(description);ui.gap(body,22);
        LinearLayout actions=new LinearLayout(this);actions.setClipChildren(false);
        TextView play=ui.action(position(title)>0?"▶  Resume":"▶  Find sources",true,()->findStreams(detailTitle));actions.addView(play);LinearLayout.LayoutParams next=new LinearLayout.LayoutParams(-2,-2);next.setMargins(ui.dp(12),0,0,0);
        TextView saved=ui.action(prefs.getBoolean("saved."+title.key(),false)?"✓  In watchlist":"+  Watchlist",false,()->{boolean value=!prefs.getBoolean("saved."+title.key(),false);prefs.edit().putBoolean("saved."+title.key(),value).apply();persist(title);showDetails(detailTitle);});actions.addView(saved,next);
        TextView episodes=ui.action("Episodes",false,()->showEpisodes(detailTitle,-1));if(title.type.equals("series"))actions.addView(episodes,next);body.addView(actions);ui.gap(body,28);
        body.addView(ui.bold("Cast & creators",19));ui.gap(body,8);TextView cast=ui.text(title.cast.isEmpty()?"Cast information has not been supplied by this source.":title.cast,14,TvUi.MUTED);body.addView(cast);ui.gap(body,25);
        body.addView(ui.bold("Your review",19));ui.gap(body,8);TextView note=ui.text(prefs.getString("review."+title.key(),"You haven't reviewed this title yet."),14,TvUi.MUTED);body.addView(note);ui.gap(body,12);body.addView(ui.action("Write a review",false,()->review(title)));
        play.requestFocus();int version=generation;
        repository.io.execute(()->{try{Library.Title full=repository.details(title);runOnUiThread(()->{if(isDestroyed()||generation!=version)return;detailTitle=full;name.setText(full.name);metadata.setText(join(full.year,full.genre,full.rating.isEmpty()?"":"★ "+full.rating));description.setText(full.description.isEmpty()?"No synopsis has been supplied by this source.":full.description);cast.setText(full.cast.isEmpty()?"Cast information has not been supplied by this source.":full.cast);persist(full);if(full.type.equals("series")&&episodes.getParent()==null)actions.addView(episodes,next);});}catch(Exception e){runOnUiThread(()->{if(generation==version)description.setText(title.description.isEmpty()?"Details unavailable. "+ProviderRepository.message(e):title.description);});}});
    }
    private void findStreams(Library.Title title){
        if(title.type.equals("series")&&(title.videos.length()>0||title.seasons.length()>0)){showEpisodes(title,-1);return;}
        TextView status=ui.text("Finding playable sources…",17,TvUi.WHITE);status.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));AlertDialog progress=new AlertDialog.Builder(this).setTitle(title.name).setView(status).setNegativeButton("Cancel",null).create();progress.show();
        repository.io.execute(()->{try{List<ProviderRepository.Stream> streams=repository.streams(title);runOnUiThread(()->{if(isDestroyed()||!progress.isShowing())return;progress.dismiss();String[] names=new String[streams.size()];for(int i=0;i<names.length;i++)names[i]=streams.get(i).name;new AlertDialog.Builder(this).setTitle("Choose a source").setItems(names,(d,w)->play(title,streams.get(w))).setNegativeButton("Back",null).show();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||!progress.isShowing())return;progress.dismiss();new AlertDialog.Builder(this).setTitle("Source unavailable").setMessage(ProviderRepository.message(e)).setPositiveButton("Back to title",null).show();});}});
    }
    private void play(Library.Title title,ProviderRepository.Stream stream){
        persist(title);Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",title.key()).putExtra("title",title.name).putExtra("url",stream.url).putExtra("headers",new JSONObject(stream.headers).toString()).putExtra("subtitles",stream.subtitles.toString());startActivity(intent);
    }
    private void showEpisodes(Library.Title title,int season){
        if(season<0&&title.seasons.length()>0){String[] names=new String[title.seasons.length()];for(int i=0;i<names.length;i++)names[i]="Season "+title.seasons.optJSONObject(i).optInt("s",i+1);new AlertDialog.Builder(this).setTitle("Choose season").setItems(names,(d,w)->showEpisodes(title,title.seasons.optJSONObject(w).optInt("s",w+1))).setNegativeButton("Back",null).show();return;}
        int version=generation;Toast.makeText(this,"Loading episodes…",Toast.LENGTH_SHORT).show();repository.io.execute(()->{try{List<Library.Title> episodes=repository.episodes(title,season);runOnUiThread(()->{if(isDestroyed()||generation!=version)return;if(episodes.isEmpty()){notice("No episodes available","This provider has not supplied episodes for this season.");return;}String[] names=new String[episodes.size()];for(int i=0;i<names.length;i++)names[i]=episodes.get(i).name;new AlertDialog.Builder(this).setTitle("Episodes").setItems(names,(d,w)->findStreams(episodes.get(w))).setNegativeButton("Back",null).show();});}catch(Exception e){runOnUiThread(()->{if(generation==version)notice("Episodes unavailable",ProviderRepository.message(e));});}});
    }
    private void showWatchlist(){shell("Watchlist",false);heading("Your watchlist","Everything you saved, in one place.");List<Library.Title> saved=new ArrayList<>();for(Library.Title t:allTitles())if(prefs.getBoolean("saved."+t.key(),false))saved.add(t);if(saved.isEmpty())body.addView(ui.text("Open a title and choose + Watchlist to save it here.",16,TvUi.MUTED));else grid(body,saved);}
    private void showSearch(){
        shell("Search",false);heading("Find your next watch","Search your library and connected sources.");EditText input=ui.input("Title, cast or genre","");body.addView(input);ui.gap(body,12);TextView status=ui.text("",12,TvUi.MUTED);LinearLayout results=ui.column();
        Runnable local=()->{results.removeAllViews();String q=input.getText().toString().toLowerCase(Locale.ROOT);List<Library.Title> matches=new ArrayList<>();for(Library.Title t:allTitles())if((t.name+" "+t.cast+" "+t.genre).toLowerCase(Locale.ROOT).contains(q))matches.add(t);status.setText(matches.size()+" matching titles in loaded catalogs");grid(results,matches);};
        Runnable remote=()->{String query=input.getText().toString().trim();if(query.isEmpty())return;status.setText("Searching sources…");int version=generation;results.removeAllViews();for(Library.Addon a:Library.addons())if(repository.enabled(a.id)&&!a.id.equals("news"))repository.io.execute(()->{try{ProviderRepository.Catalog found=repository.load(a,query,1);runOnUiThread(()->{if(generation!=version)return;if(!found.titles.isEmpty())shelf(results,a.name,found.titles,true);status.setText("Search results · some providers may be unavailable");});}catch(Exception ignored){runOnUiThread(()->{if(generation==version)status.setText("Search complete for available sources");});}});};
        body.addView(ui.action("Search connected sources",true,remote));ui.gap(body,14);body.addView(status);ui.gap(body,16);body.addView(results);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setOnEditorActionListener((v,id,event)->{remote.run();return true;});input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){local.run();}public void afterTextChanged(Editable e){}});local.run();
    }
    private void showAddons(){
        shell("Add-ons",false);heading("Your sources","Native catalogs. Native playback. No website launchers.");
        for(Library.Addon a:Library.addons()){
            LinearLayout panel=ui.column();panel.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(16));panel.setBackground(ui.shape(0xff151b26,0,12));
            LinearLayout line=new LinearLayout(this);line.addView(ui.bold(a.name,21),new LinearLayout.LayoutParams(0,-2,1));ProviderRepository.Catalog cat=catalogs.get(a.id);
            String state=!repository.enabled(a.id)?"Disabled":cat!=null?cat.titles.size()+" titles":"Needs attention";line.addView(ui.text(state,12,cat!=null?0xff94c8a3:TvUi.MUTED));panel.addView(line);ui.gap(panel,8);TextView info=ui.text(states.getOrDefault(a.id,a.description),12,TvUi.MUTED);info.setMaxLines(2);panel.addView(info);ui.gap(panel,14);
            LinearLayout actions=new LinearLayout(this);actions.addView(ui.action("Browse",true,()->showProvider(a,1)));LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-2,-2);margin.setMargins(ui.dp(10),0,0,0);actions.addView(ui.action("Configure",false,()->configure(a)),margin);actions.addView(ui.action(repository.enabled(a.id)?"Disable":"Enable",false,()->{prefs.edit().putBoolean("addon."+a.id,!repository.enabled(a.id)).apply();showAddons();if(repository.enabled(a.id))load(a,1);}),margin);panel.addView(actions);body.addView(panel);ui.gap(body,14);
        }
    }
    private void configure(Library.Addon addon){
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(14),ui.dp(24),ui.dp(10));box.addView(ui.text("Connect an HTTPS catalog and stream provider using its manifest URL.",14,TvUi.MUTED));ui.gap(box,12);EditText endpoint=ui.input("https://your-provider/manifest.json",repository.endpoint(addon.id));box.addView(endpoint);
        if(addon.id.equals("netmirror")){ui.gap(box,14);box.addView(ui.action("NetMirror API settings",false,this::netmirrorSettings));}
        new AlertDialog.Builder(this).setTitle(addon.name).setView(box).setPositiveButton("Save",(d,w)->{try{repository.configure(addon.id,endpoint.getText().toString().trim());states.put(addon.id,"Refreshing…");load(addon,1);}catch(Exception e){notice("Invalid provider",ProviderRepository.message(e));}}).setNegativeButton("Cancel",null).show();
    }
    private void netmirrorSettings(){
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(10));box.addView(ui.text("Uses the API format found in the repository you supplied. Server availability and valid sessions are controlled by NetMirror.",13,TvUi.MUTED));ui.gap(box,12);
        EditText api=ui.input("API base URL",prefs.getString("netmirror.api","https://net50.cc/mobile"));box.addView(api);ui.gap(box,10);EditText session=ui.input("Session token supplied by NetMirror (optional)",prefs.getString("netmirror.session",""));session.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(session);ui.gap(box,10);
        Spinner platform=new Spinner(this);String[] platforms={"Netflix","Prime Video","Disney+ Hotstar"};String[] values={"nf","pv","hs"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,platforms);platform.setAdapter(adapter);for(int i=0;i<values.length;i++)if(values[i].equals(prefs.getString("netmirror.ott","nf")))platform.setSelection(i);box.addView(platform);
        new AlertDialog.Builder(this).setTitle("NetMirror native API").setView(box).setPositiveButton("Save",(d,w)->{String base=api.getText().toString().trim();if(!Library.https(base)){notice("Invalid API","Use an HTTPS API base URL.");return;}prefs.edit().putString("netmirror.api",base).putString("netmirror.session",session.getText().toString().trim()).putString("netmirror.ott",values[platform.getSelectedItemPosition()]).apply();load(addonFor("netmirror"),1);}).setNegativeButton("Cancel",null).show();
    }
    private void showSettings(){
        shell("Settings",false);heading("Make yourself at home","Astra TV 0.2 · native edition");
        body.addView(ui.action("Import your media catalog",true,()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),20)));ui.gap(body,14);
        body.addView(ui.action("Background audio · "+(prefs.getBoolean("background",false)?"On":"Off"),false,()->{prefs.edit().putBoolean("background",!prefs.getBoolean("background",false)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Refresh connected sources",false,()->{refreshAll();Toast.makeText(this,"Refreshing sources",Toast.LENGTH_SHORT).show();}));ui.gap(body,14);
        body.addView(ui.action("Stop playback",false,()->{stopService(new Intent(this,PlaybackService.class));Toast.makeText(this,"Playback stopped",Toast.LENGTH_SHORT).show();}));ui.gap(body,14);
        body.addView(ui.action("Clear watch history",false,()->{android.content.SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith("position."))edit.remove(key);edit.apply();Toast.makeText(this,"History cleared",Toast.LENGTH_SHORT).show();}));ui.gap(body,24);
        body.addView(ui.text("Playback supports MP4, HLS and DASH, embedded audio tracks, and supplied subtitles. A source must expose playable media; website-only and protected sources can be unavailable.",14,TvUi.MUTED));
    }
    private void review(Library.Title title){
        EditText text=ui.input("What did you think?",prefs.getString("review."+title.key(),""));text.setSingleLine(false);text.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("Your review · "+title.name).setView(text).setPositiveButton("Save",(d,w)->{prefs.edit().putString("review."+title.key(),text.getText().toString()).apply();persist(title);showDetails(title);}).setNegativeButton("Cancel",null).show();
    }
    private void heading(String title,String subtitle){ui.gap(body,18);body.addView(ui.bold(title,32));ui.gap(body,9);TextView info=ui.text(subtitle,14,TvUi.MUTED);info.setMaxLines(3);body.addView(info);ui.gap(body,24);}
    private void notice(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private Library.Addon addonFor(String id){for(Library.Addon a:Library.addons())if(a.id.equals(id))return a;return null;}
    private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<600;}
    private static String join(String...strings){List<String> out=new ArrayList<>();for(String s:strings)if(s!=null&&!s.isEmpty())out.add(s);return String.join("  ·  ",out);}
    private int position(Library.Title title){return prefs.getInt("position."+title.key(),0);}
    private void migrateLegacy(){
        List<Library.Title> old=new ArrayList<>(Library.titles(this));old.addAll(Library.samples());android.content.SharedPreferences.Editor edit=prefs.edit();
        for(Library.Title t:old){for(String prefix:new String[]{"saved.","review.","position."}){String before=prefix+t.id,after=prefix+t.key();if(prefs.contains(before)&&!prefs.contains(after)){Object value=prefs.getAll().get(before);if(value instanceof Boolean)edit.putBoolean(after,(Boolean)value);else if(value instanceof Integer)edit.putInt(after,(Integer)value);else if(value instanceof String)edit.putString(after,(String)value);}}}edit.apply();
    }
    private void persist(Library.Title title){try{prefs.edit().putString("title."+title.key(),title.json().toString()).apply();}catch(JSONException ignored){}}
    private List<Library.Title> allTitles(){
        Map<String,Library.Title> titles=new LinkedHashMap<>();for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().startsWith("title."))try{Library.Title t=Library.Title.from(new JSONObject(e.getValue().toString()));titles.put(t.key(),t);}catch(Exception ignored){}
        for(ProviderRepository.Catalog c:catalogs.values())for(Library.Title t:c.titles)titles.putIfAbsent(t.key(),t);for(Library.Title t:Library.titles(this))titles.put(t.key(),t);for(Library.Title t:Library.samples())titles.put(t.key(),t);return new ArrayList<>(titles.values());
    }
    private View findTagged(View view,String tag){if(tag.equals(view.getTag()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findTagged(group.getChildAt(i),tag);if(found!=null)return found;}}return null;}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=20||result!=RESULT_OK||data==null)return;
        repository.io.execute(()->{try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException("Catalog must be under 1 MB");out.write(b,0,n);}String json=out.toString("UTF-8");int count=Library.parse(json).size();prefs.edit().putString("catalog",json).apply();runOnUiThread(()->{if(!isDestroyed()){Toast.makeText(this,"Imported "+count+" videos",Toast.LENGTH_LONG).show();showHome();}});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Import failed",ProviderRepository.message(e));});}});
    }
    @Override public void onBackPressed(){if(!screen.equals("Home"))showHome();else super.onBackPressed();}
    @Override protected void onDestroy(){generation++;main.removeCallbacksAndMessages(null);repository.io.shutdownNow();super.onDestroy();}
}
