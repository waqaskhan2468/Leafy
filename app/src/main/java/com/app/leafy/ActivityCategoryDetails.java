package com.app.leafy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.app.leafy.adapter.AdapterProduct;
import com.app.leafy.adapter.AdapterShoppingCart;
import com.app.leafy.connection.API;
import com.app.leafy.connection.RestAdapter;
import com.app.leafy.connection.callbacks.CallbackProduct;
import com.app.leafy.connection.callbacks.CallbackProductDetails;
import com.app.leafy.data.AppConfig;
import com.app.leafy.data.Constant;
import com.app.leafy.data.DatabaseHandler;
import com.app.leafy.model.Cart;
import com.app.leafy.model.Category;
import com.app.leafy.model.Product;
import com.app.leafy.utils.NetworkCheck;
import com.app.leafy.utils.Tools;
import com.balysv.materialripple.MaterialRippleLayout;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityCategoryDetails extends AppCompatActivity {
    private static final String EXTRA_OBJECT = "key.EXTRA_OBJECT";

    // activity transition
    public static void navigate(Activity activity, Category obj) {
        Intent i = new Intent(activity, ActivityCategoryDetails.class);
        i.putExtra(EXTRA_OBJECT, obj);
        activity.startActivity(i);
    }

    // extra obj
    private Category category;
    private Product product;
    private AdapterShoppingCart adapter;
    private DatabaseHandler db;
    private Toolbar toolbar;
    private ActionBar actionBar;
    private View parent_view;
    private SwipeRefreshLayout swipe_refresh;
    private Call<CallbackProduct> callbackCall = null;
    private Call<CallbackProductDetails> callbackCallProduct = null;
    private TextView price_total,priceDiscount;
    private ImageView image;
    private RecyclerView recyclerView;
    private AdapterProduct mAdapter;
    //private Product model;
    private double quantity = 1;
    private Button btn_cart,btn_ContineShop,btn_GoToCart;
    private LinearLayout lyt_quantity,lyt_price;

    private int post_total = 0;
    private int failed_page = 0;
    private boolean flag_cart = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_details);
        db = new DatabaseHandler(this);
        parent_view = findViewById(android.R.id.content);
        category = (Category) getIntent().getSerializableExtra(EXTRA_OBJECT);
        initComponent();
        initToolbar();

        displayCategoryData(category);
        requestAction(1);
    }

    private void initComponent() {
        swipe_refresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, Tools.getGridSpanCount(this)));
        recyclerView.setHasFixedSize(true);
        //price_total = (TextView) findViewById(R.id.price_total);
        //set data and list adapter
        mAdapter = new AdapterProduct(this, recyclerView, new ArrayList<Product>());
        recyclerView.setAdapter(mAdapter);

        mAdapter.setOnItemClickListener(new AdapterProduct.OnItemClickListener() {
            @Override
            public void onItemClick(View view, Product obj,int position) {
                //Toast.makeText(ActivityCategoryDetails.this, "Obj Id = "+obj.id, Toast.LENGTH_SHORT).show();
                requestProductDetailAction(obj.id);
            }
        });


        // detect when scroll reach bottom
        mAdapter.setOnLoadMoreListener(new AdapterProduct.OnLoadMoreListener() {
            @Override
            public void onLoadMore(int current_page) {
                if (post_total > mAdapter.getItemCount() && current_page != 0) {
                    int next_page = current_page + 1;
                    requestAction(next_page);
                } else {
                    mAdapter.setLoaded();
                }
            }
        });

        // on swipe list
        swipe_refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (callbackCall != null && callbackCall.isExecuted()) callbackCall.cancel();
                mAdapter.resetListData();
                requestAction(1);
            }
        });
    }

    //Used to set Toolbar Category Detail
    private void displayCategoryData(Category c) {
        ((AppBarLayout) findViewById(R.id.app_bar_layout)).setBackgroundColor(Color.parseColor(c.color));
        ((TextView) findViewById(R.id.name)).setText(c.name);
        ((TextView) findViewById(R.id.brief)).setText(c.brief);
        ImageView icon = (ImageView) findViewById(R.id.icon);
        Tools.displayImageOriginal(this, icon, Constant.getURLimgCategory(c.icon));
        Tools.setSystemBarColorDarker(this, c.color);
        if (AppConfig.TINT_CATEGORY_ICON) {
            icon.setColorFilter(Color.WHITE);
        }

        // analytics track
        ThisApplication.getInstance().saveLogEvent(c.id, c.name, "CATEGORY_DETAILS");
    }

    private void initToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setTitle("");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_category_details, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int item_id = item.getItemId();
        if(item_id == android.R.id.home){
            super.onBackPressed();
        } else if(item_id == R.id.action_search){
            ActivitySearch.navigate(ActivityCategoryDetails.this, category);
        } else if(item_id == R.id.action_cart){
            Intent i = new Intent(this, ActivityShoppingCart.class);
            startActivity(i);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    private void displayApiResult(final List<Product> items) {

        mAdapter.insertData(items);
        swipeProgress(false);
        if (items.size() == 0) showNoItemView(true);
    }

    private void requestListProduct(final int page_no) {
        API api = RestAdapter.createAPI();
        callbackCall = api.getListProduct(page_no, Constant.PRODUCT_PER_REQUEST, null, category.id);
        callbackCall.enqueue(new Callback<CallbackProduct>() {
            @Override
            public void onResponse(Call<CallbackProduct> call, Response<CallbackProduct> response) {
                CallbackProduct resp = response.body();
                if (resp != null && resp.status.equals("success")) {
                    post_total = resp.count_total;
                   // Toast.makeText(ActivityCategoryDetails.this, ""+resp.products.get(0).description, Toast.LENGTH_SHORT).show();
                    displayApiResult(resp.products);

                } else {
                    onFailRequest(page_no);
                }
            }

            @Override
            public void onFailure(Call<CallbackProduct> call, Throwable t) {
                if (!call.isCanceled()) onFailRequest(page_no);
            }

        });
    }

    private void requestProductDetailAction(final Long product_id) {
        showFailedView(false, "");
        swipeProgress(true);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                requestProductDetailApi(product_id);
            }
        }, 1000);
    }

    private void requestProductDetailApi(Long product_id) {
        API api = RestAdapter.createAPI();
        callbackCallProduct = api.getProductDetails(product_id);
        callbackCallProduct.enqueue(new Callback<CallbackProductDetails>() {
            @Override
            public void onResponse(Call<CallbackProductDetails> call, Response<CallbackProductDetails> response) {
                CallbackProductDetails resp = response.body();
                if (resp != null && resp.status.equals("success")) {
                    product = resp.product;
                    //Toast.makeText(ActivityCategoryDetails.this,"product "+product.description, Toast.LENGTH_SHORT).show();
                    dialogProductAction(product);
                    swipeProgress(false);
                } else {
                    onFailProductRequest();
                }
            }

            @Override
            public void onFailure(Call<CallbackProductDetails> call, Throwable t) {
                Log.e("onFailure", t.getMessage());
                if (!call.isCanceled()) onFailProductRequest();
            }
        });
    }

    private void onFailRequest(int page_no) {
        failed_page = page_no;
        mAdapter.setLoaded();
        swipeProgress(false);
        if (NetworkCheck.isConnect(this)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    private void onFailProductRequest() {
        swipeProgress(false);
        if (NetworkCheck.isConnect(this)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    private void requestAction(final int page_no) {
        showFailedView(false, "");
        showNoItemView(false);
        if (page_no == 1) {
            swipeProgress(true);
        } else {
            mAdapter.setLoading();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                requestListProduct(page_no);
            }
        }, 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        swipeProgress(false);
        if (callbackCall != null && callbackCall.isExecuted()) {
            callbackCall.cancel();
        }
    }

    private void showFailedView(boolean show, String message) {
        View lyt_failed = (View) findViewById(R.id.lyt_failed);
        ((TextView) findViewById(R.id.failed_message)).setText(message);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            lyt_failed.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            lyt_failed.setVisibility(View.GONE);
        }
        ((Button) findViewById(R.id.failed_retry)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestAction(failed_page);
            }
        });
    }

    private void showNoItemView(boolean show) {
        View lyt_no_item = (View) findViewById(R.id.lyt_no_item);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            lyt_no_item.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            lyt_no_item.setVisibility(View.GONE);
        }
    }

    private void swipeProgress(final boolean show) {
        if (!show) {
            swipe_refresh.setRefreshing(show);
            return;
        }
        swipe_refresh.post(new Runnable() {
            @Override
            public void run() {
                swipe_refresh.setRefreshing(show);
            }
        });
    }


    private void dialogProductAction(final Product model) {
        //Toast.makeText(this, "description" +model.description, Toast.LENGTH_SHORT).show();
        final Dialog dialog = new Dialog(ActivityCategoryDetails.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); // before
        dialog.setContentView(R.layout.dialog_product_details);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        image = (ImageView) dialog.findViewById(R.id.image1);
        lyt_quantity = (LinearLayout) dialog.findViewById(R.id.lyt_quantity_text);
        lyt_price = (LinearLayout) dialog.findViewById(R.id.lyt_price);
        Tools.displayImageOriginal(ActivityCategoryDetails.this, image, Constant.getURLimgProduct(model.image));
        ((TextView) dialog.findViewById(R.id.title)).setText(model.name);

        ((TextView) dialog.findViewById(R.id.price)).setText(Tools.getFormattedPrice(model.price_discount , ActivityCategoryDetails.this)+"/"+model.description);
        //Toast.makeText(ActivityCategoryDetails.this, category.name+"   catego", Toast.LENGTH_LONG).show();
        priceDiscount = (TextView) dialog.findViewById(R.id.price_strike);
//        priceDiscount.setText(model.price+"");
        priceDiscount.setText(Tools.getFormattedPrice(model.price, ActivityCategoryDetails.this));
        priceDiscount.setPaintFlags(priceDiscount.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        //((TextView) dialog.findViewById(R.id.stock)).setText(getString(R.string.stock) + model.stock);
        final TextView qty = (TextView) dialog.findViewById(R.id.quantity);
        btn_cart = (Button) dialog.findViewById(R.id.btn_cart);
        btn_ContineShop=(Button)dialog.findViewById(R.id.btn_continueShopping);
        btn_GoToCart=(Button)dialog.findViewById(R.id.btn_GoCart);
        refreshCartButton(model);
        qty.setText((int)quantity + " "+model.description);
        ((ImageView) dialog.findViewById(R.id.img_decrease)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(category.name.equals("Fruits")){
                    if (quantity > 1.0) {
                        quantity--;
                        qty.setText((int) quantity + " " + model.description);
                    }
                }
                else if(category.name.equals("Vegetables")){
                    if (quantity > 2.0) {
                        quantity--;
                        qty.setText((int)quantity +" "+ model.description);
                    }
                    else if ((quantity <= 2.0)&&(quantity > 0.5)){
                        quantity = quantity - 0.5;
                        qty.setText(quantity +" "+ model.description);
                    }
                }
            }
        });
        ((ImageView) dialog.findViewById(R.id.img_increase)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(category.name.equals("Fruits")){
                    quantity++;
                    qty.setText((int) quantity + " "+model.description);
                }
                else if(category.name.equals("Vegetables")){
                    if ((quantity >= 0.5) && (quantity < 2.0)) {
                        quantity = quantity + 0.25;
                        qty.setText(quantity +" "+model.description);
                } else if (quantity >= 2.0) {
                    quantity++;
                    qty.setText((int) quantity +" "+model.description);
                }
            }}
        });

        btn_cart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (model == null || (model.name != null && model.name.equals(""))) {
                    Toast.makeText(getApplicationContext(), R.string.please_wait_text, Toast.LENGTH_SHORT).show();
                    return;
                }
                toggleCartButton(model);
            }
        });
        dialog.show();
        dialog.getWindow().setAttributes(lp);
        btn_GoToCart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ActivityCategoryDetails.this, ActivityShoppingCart.class);
                startActivity(i);
            }
        });
        btn_ContineShop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private void toggleCartButton(Product model) {
        if (flag_cart) {
            db.deleteActiveCart(model.id);
        } else {
            // check stock product
            if (model.status.equalsIgnoreCase("SUSPEND")) {
                Toast.makeText(this, R.string.msg_suspend, Toast.LENGTH_SHORT).show();
                return;
            }
           // Toast.makeText(ActivityCategoryDetails.this, ""+model.price, Toast.LENGTH_SHORT).show();
            Double selected_price = model.price_discount > 0 ? model.price_discount : model.price;
            Cart cart = new Cart(model.id, model.name, model.image, quantity,model.description, selected_price, System.currentTimeMillis(),model.price);
            db.saveCart(cart);
        }
        refreshCartButton(model);
    }
    private void refreshCartButton(Product model) {
        Cart c = db.getCart(model.id);
        flag_cart = (c != null);
        if (flag_cart) {
            quantity=c.amount;
            lyt_quantity.setVisibility(View.GONE);
            lyt_price.setVisibility(View.GONE);
            btn_cart.setBackgroundColor(getResources().getColor(R.color.colorRemoveCart));
            btn_cart.setText(R.string.bt_remove_cart);
            btn_GoToCart.setVisibility(View.VISIBLE);
            btn_ContineShop.setVisibility(View.VISIBLE);
            btn_cart.setVisibility(View.GONE);
        } else {
            lyt_quantity.setVisibility(View.VISIBLE);
            lyt_price.setVisibility(View.VISIBLE);
            btn_cart.setBackgroundColor(getResources().getColor(R.color.colorAddCart));
            btn_cart.setText(R.string.bt_add_cart);

        }
    }
}