package com.astratv.app;

import android.view.*;
import android.widget.*;
import com.astratv.app.coreui.*;
import java.util.Collections;

/** Frosted combinable filter rail. Persistence is astra_ui_v1 only. */
final class FilterPanel {
    private FilterPanel(){}
    static View create(android.content.Context context,TvUi ui,BrowseFilter filter,Runnable apply,Runnable close,Runnable rebuild){
        GlassPanel panel=new GlassPanel(context,20);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);
        LinearLayout box=ui.column();box.setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(24));
        box.addView(ui.bold("Filters",22));ui.gap(box,6);box.addView(ui.text("Combinable. Newest first unless you pick another sort.",12,TvUi.MUTED));ui.gap(box,16);
        LinearLayout header=new LinearLayout(context);header.addView(ui.action("Apply",true,apply));LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(-2,-2);m.leftMargin=ui.dp(8);header.addView(ui.action("Clear",false,()->{filter.genres.clear();filter.languages.clear();filter.providers.clear();filter.types.clear();filter.yearMin=filter.yearMax=filter.runtimeMin=filter.runtimeMax=0;filter.minRating=0;filter.country=filter.contentRating=filter.quality="";filter.watched="all";filter.sort="newest";apply.run();}),m);header.addView(ui.action("Close",false,close),m);box.addView(header);ui.gap(box,18);
        section(ui,box,"Sort");chips(context,ui,box,CatalogSort.MODES.toArray(new String[0]),Collections.singleton(filter.sort),one->{filter.sort=one;rebuild.run();},rebuild);
        section(ui,box,"Type");chips(context,ui,box,new String[]{"movie","series","tv"},filter.types,null,rebuild);
        java.util.List<String> genres=new java.util.ArrayList<>(GenreTaxonomy.BROWSE);genres.add(GenreTaxonomy.OTHER);
        section(ui,box,"Genre · pick any");chips(context,ui,box,genres.toArray(new String[0]),filter.genres,null,rebuild);
        section(ui,box,"Language · pick any");chips(context,ui,box,LanguageTaxonomy.ALL.toArray(new String[0]),filter.languages,null,rebuild);
        section(ui,box,"Country");chips(context,ui,box,new String[]{"India","United States","United Kingdom","Japan","South Korea"},filter.country.isEmpty()?Collections.emptySet():Collections.singleton(filter.country),one->{filter.country=one.equals(filter.country)?"":one;rebuild.run();},rebuild);
        section(ui,box,"Content rating");chips(context,ui,box,new String[]{"G","PG","PG-13","U","UA","A"},filter.contentRating.isEmpty()?Collections.emptySet():Collections.singleton(filter.contentRating),one->{filter.contentRating=one.equals(filter.contentRating)?"":one;rebuild.run();},rebuild);
        section(ui,box,"Year");
        LinearLayout years=new LinearLayout(context);years.addView(ui.action(filter.yearMin>0?("From "+filter.yearMin):"From year",false,()->pickYear(context,y->{filter.yearMin=y;rebuild.run();})));years.addView(ui.action(filter.yearMax>0?("To "+filter.yearMax):"To year",false,()->pickYear(context,y->{filter.yearMax=y;rebuild.run();})),m);box.addView(years);ui.gap(box,12);
        section(ui,box,"Minimum rating");chips(context,ui,box,new String[]{"0","6","7","8","9"},Collections.singleton(filter.minRating<=0?"0":String.valueOf((int)filter.minRating)),one->{filter.minRating="0".equals(one)?0:Float.parseFloat(one);rebuild.run();},rebuild);
        section(ui,box,"Watched");chips(context,ui,box,new String[]{"all","watched","unwatched"},Collections.singleton(filter.watched),one->{filter.watched=one;rebuild.run();},rebuild);
        section(ui,box,"Source");
        java.util.List<String> ids=new java.util.ArrayList<>();for(Library.Addon a:Library.addons())ids.add(a.id);ids.add("library");ids.add("samples");
        chips(context,ui,box,ids.toArray(new String[0]),filter.providers,null,rebuild);
        section(ui,box,"Quality");chips(context,ui,box,new String[]{"4K","1080p","720p","HD","SD"},filter.quality.isEmpty()?java.util.Collections.emptySet():java.util.Collections.singleton(filter.quality),one->{filter.quality=one.equals(filter.quality)?"":one;rebuild.run();},rebuild);
        scroll.addView(box);panel.addView(scroll,new FrameLayout.LayoutParams(-1,-1));return panel;
    }
    private static void section(TvUi ui,LinearLayout box,String name){box.addView(ui.bold(name,15));ui.gap(box,8);}
    private static void chips(android.content.Context context,TvUi ui,LinearLayout box,String[] options,java.util.Set<String> selected,java.util.function.Consumer<String> exclusive,Runnable rebuild){
        LinearLayout row=null;int i=0;
        for(String option:options){
            if(i%3==0){row=new LinearLayout(context);row.setClipChildren(false);box.addView(row);ui.gap(box,8);}
            boolean on=selected.contains(option);
            TextView chip=ui.action(label(option),on,()->{
                if(exclusive!=null)exclusive.accept(option);
                else{if(!selected.add(option))selected.remove(option);rebuild.run();}
            });
            chip.setTextSize(12);chip.setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=ui.dp(6);row.addView(chip,p);i++;
        }
        ui.gap(box,10);
    }
    private static String label(String option){
        if(CatalogSort.MODES.contains(option))return CatalogSort.label(option);if(option.equals("tv"))return "Live";
        if(option.equals("Science Fiction"))return GenreTaxonomy.display(option);
        if(option.equals("movie"))return "Movies";if(option.equals("series"))return "Shows";
        for(Library.Addon a:Library.addons())if(a.id.equals(option))return a.name;
        return option.substring(0,1).toUpperCase(java.util.Locale.ROOT)+option.substring(1);
    }
    private static void pickYear(android.content.Context context,java.util.function.IntConsumer into){
        String[] years=new String[36];years[0]="Any";for(int i=1;i<years.length;i++)years[i]=String.valueOf(2026-i+1);
        new android.app.AlertDialog.Builder(context).setTitle("Year").setItems(years,(d,w)->into.accept(w==0?0:Integer.parseInt(years[w]))).setNegativeButton("Cancel",null).show();
    }
}
