package com.app.leafy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivitySearch extends AppCompatActivity {

    private static final String EXTRA_CATEGORY_ID = "key.EXTRA_CATEGORY_ID";
    private static final String EXTRA_CATEGORY_NAME = "key.EXTRA_CATEGORY_NAME";

    // activity transition
    public static void navigate(Activity activity, Category category) {
        Intent i = new Intent(activity, ActivitySearch.class);
        i.putExtra(EXTRA_CATEGORY_ID, category.id);
        i.putExtra(EXTRA_CATEGORY_NAME, category.name);
        activity.startActivity(i);
    }

    // activity transition
    public static void navigate(Activity activity) {
        Intent i = new Intent(activity, ActivitySearch.class);
        i.putExtra(EXTRA_CATEGORY_NAME, activity.getString(R.string.ALL));
        activity.startActivity(i);
    }

    private Toolbar toolbar;
    private DatabaseHandler db;
    private ActionBar actionBar;
    private EditText et_search;
    private RecyclerView recyclerView;
    private AdapterProduct adapterProduct;
    private ImageButton bt_clear;
    private View parent_view;
    private SwipeRefreshLayout swipe_refresh;
    private Call<CallbackProduct> callbackCall = null;
    private Call<CallbackProductDetails> callbackCallProduct = null;
    private Product product;
    private Category category;
    private ImageView image;
    private LinearLayout lyt_quantity,lyt_price;
    private TextView priceDiscount;
    private Button btn_cart;
    private double quantity = 1;
    private boolean flag_cart = false;
    private int post_total = 0;
    private int failed_page = 0;
    private long category_id = -1L;
    private String category_name;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        db = new DatabaseHandler(this);
        category_name = getString(R.string.ALL);
        category_id = getIntent().getLongExtra(EXTRA_CATEGORY_ID, -1L);
        category_name = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);

        initComponent();
        setupToolbar();
    }

    private void initComponent() {
        parent_view = findViewById(android.R.id.content);
        swipe_refresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        et_search = (EditText) findViewById(R.id.et_search);
        et_search.addTextChangedListener(textWatcher);

        bt_clear = (ImageButton) findViewById(R.id.bt_clear);
        ((TextView) findViewById(R.id.category)).setText(getString(R.string.Category) + category_name);
        bt_clear.setVisibility(View.GONE);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new GridLayoutManager(this, Tools.getGridSpanCount(this)));
        recyclerView.setHasFixedSize(true);
        //set data and list adapter
        adapterProduct = new AdapterProduct(this, recyclerView, new ArrayList<Product>());
        recyclerView.setAdapter(adapterProduct);
        adapterProduct.setOnItemClickListener(new AdapterProduct.OnItemClickListener() {
            @Override
            public void onItemClick(View v, Product obj, int pos) {
                //ActivityProductDetails.navigate(ActivitySearch.this, obj.id, false);
                requestProductDetailAction(obj.id);
            }
        });

        // detect when scroll reach bottom
        adapterProduct.setOnLoadMoreListener(new AdapterProduct.OnLoadMoreListener() {
            @Override
            public void onLoadMore(int current_page) {
                if (post_total > adapterProduct.getItemCount() && current_page != 0) {
                    int next_page = current_page + 1;
                    requestAction(next_page);
                } else {
                    adapterProduct.setLoaded();
                }
            }
        });

        bt_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                et_search.setText("");
                adapterProduct.resetListData();
                showNoItemView(true);
            }
        });

        et_search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard();
                    searchAction();
                    return true;
                }
                return false;
            }
        });

        // on swipe list
        swipe_refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (callbackCall != null && callbackCall.isExecuted()) callbackCall.cancel();
                adapterProduct.resetListData();
                requestAction(1);
            }
        });

        showNoItemView(true);
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

    private void onFailProductRequest() {
        swipeProgress(false);
        if (NetworkCheck.isConnect(this)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    private void dialogProductAction(final Product model) {
        //Toast.makeText(this, "description" +model.description, Toast.LENGTH_SHORT).show();
        final Dialog dialog = new Dialog(ActivitySearch.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); // before
        dialog.setContentView(R.layout.dialog_product_details);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        image = (ImageView) dialog.findViewById(R.id.image1);
        lyt_quantity = (LinearLayout) dialog.findViewById(R.id.lyt_quantity_text);
        lyt_price = (LinearLayout) dialog.findViewById(R.id.lyt_price);
        Tools.displayImageOriginal(ActivitySearch.this, image, Constant.getURLimgProduct(model.image));
        ((TextView) dialog.findViewById(R.id.title)).setText(model.name);
        ((TextView) dialog.findViewById(R.id.price)).setText(Tools.getFormattedPrice(model.price_discount, ActivitySearch.this));
        priceDiscount = (TextView) dialog.findViewById(R.id.price_strike);
        priceDiscount.setText(Tools.getFormattedPrice(model.price, ActivitySearch.this));
        priceDiscount.setPaintFlags(priceDiscount.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        final TextView qty = (TextView) dialog.findViewById(R.id.quantity);
        btn_cart = (Button) dialog.findViewById(R.id.btn_cart);
        final Category c = model.categories.get(0);
        refreshCartButton(model);
        qty.setText((int)quantity + " "+model.description);
        ((ImageView) dialog.findViewById(R.id.img_decrease)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(c.name.equals("Fruits")){
                    if (quantity > 1.0) {
                        quantity--;
                        qty.setText((int) quantity + " " + model.description);
                    }
                }
                else if(c.name.equals("Vegetables")){
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
                if(c.name.equals("Fruits")){
                    quantity++;
                    qty.setText((int) quantity + " "+model.description);
                }
                else if(c.name.equals("Vegetables")){
                    if ((quantity >= 0.5) && (quantity < 2.0)) {
                        quantity = quantity + 0.5;
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
        } else {
            lyt_quantity.setVisibility(View.VISIBLE);
            lyt_price.setVisibility(View.VISIBLE);
            btn_cart.setBackgroundColor(getResources().getColor(R.color.colorAddCart));
            btn_cart.setText(R.string.bt_add_cart);
        }
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
            Toast.makeText(ActivitySearch.this, "Adding To Cart", Toast.LENGTH_SHORT).show();
            Double selected_price = model.price_discount > 0 ? model.price_discount : model.price;
            Cart cart = new Cart(model.id, model.name, model.image, quantity,model.description, selected_price, System.currentTimeMillis(),model.price);
            db.saveCart(cart);
            Toast.makeText(ActivitySearch.this, "Product Added", Toast.LENGTH_SHORT).show();
        }
        refreshCartButton(model);
    }

    private void searchAction() {
        query = et_search.getText().toString().trim();
        if (!query.equals("")) {
            adapterProduct.resetListData();
            // request action will be here
            requestAction(1);
        } else {
            Toast.makeText(this, R.string.please_fill, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestAction(final int page_no) {
        showFailedView(false, "");
        showNoItemView(false);
        if (page_no == 1) {
            swipeProgress(true);
        } else {
            adapterProduct.setLoading();
        }

        // analytics track
        ThisApplication.getInstance().saveCustomLogEvent("SEARCH_PRODUCT", "keyword", query);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                requestListProduct(page_no);
            }
        }, 1000);
    }

    private void setupToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }


    private void requestListProduct(final int page_no) {
        API api = RestAdapter.createAPI();
        callbackCall = api.getListProduct(page_no, Constant.PRODUCT_PER_REQUEST, query, category_id);
        callbackCall.enqueue(new Callback<CallbackProduct>() {
            @Override
            public void onResponse(Call<CallbackProduct> call, Response<CallbackProduct> response) {
                CallbackProduct resp = response.body();
                if (resp != null && resp.status.equals("success")) {
                    post_total = resp.count_total;
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

    private void displayApiResult(final List<Product> items) {
        adapterProduct.insertData(items);
        swipeProgress(false);
        if (items.size() == 0) showNoItemView(true);
    }

    private void onFailRequest(int page_no) {
        failed_page = page_no;
        adapterProduct.setLoaded();
        swipeProgress(false);
        if (NetworkCheck.isConnect(this)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    @Override
    protected void onResume() {
        adapterProduct.notifyDataSetChanged();
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence c, int i, int i1, int i2) {
            if (c.toString().trim().length() == 0) {
                bt_clear.setVisibility(View.GONE);
            } else {
                bt_clear.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    };

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
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

}

