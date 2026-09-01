
package io.github.sky.apperrors.utils.factory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.viewbinding.ViewBinding;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


public class BaseAdapterFactoryKt {

    
    public static BaseAdapter bindAdapter(ListView listView, java.util.function.Consumer<BaseAdapterCreater> initiate) {
        BaseAdapterCreater creater = new BaseAdapterCreater(listView.getContext());
        if (initiate != null) initiate.accept(creater);
        BaseAdapter adapter = creater.baseAdapter;
        if (adapter == null) throw new IllegalStateException("BaseAdapter not binded");
        listView.setAdapter(adapter);
        return adapter;
    }

    
    public static class BaseAdapterCreater {

        private final Context context;

        
        public Supplier<List<?>> listDataCallback;

        
        public BaseAdapter baseAdapter;

        public BaseAdapterCreater(Context context) {
            this.context = context;
        }

        
        public void onBindDatas(Supplier<List<?>> result) {
            listDataCallback = result;
        }

        
        public <VB extends ViewBinding> void onBindViews(Class<VB> bindingClass, BiConsumer<VB, Integer> bindViews) {
            baseAdapter = new BaseAdapter() {
                @Override
                public int getCount() {
                    return listDataCallback != null && listDataCallback.get() != null ? listDataCallback.get().size() : 0;
                }

                @Override
                public Object getItem(int position) {
                    return listDataCallback != null && listDataCallback.get() != null ? listDataCallback.get().get(position) : null;
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                @SuppressWarnings("unchecked")
                public View getView(int position, View convertView, ViewGroup parent) {
                    View holderView = convertView;
                    VB holder;
                    if (convertView == null) {
                        try {
                            Method m = bindingClass.getMethod("inflate", LayoutInflater.class);
                            holder = (VB) m.invoke(null, LayoutInflater.from(context));
                        } catch (Exception e) {
                            throw new IllegalStateException("ViewHolder binding failed", e);
                        }
                        if (holder == null) throw new IllegalStateException("ViewHolder binding failed");
                        holderView = holder.getRoot();
                        holderView.setTag(holder);
                    } else {
                        holder = (VB) convertView.getTag();
                    }
                    bindViews.accept(holder, position);
                    return holderView != null ? holderView : new View(context);
                }
            };
        }
    }

    private BaseAdapterFactoryKt() {}
}
