package com.example.coolweather;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.coolweather.R;
import com.example.coolweather.db.City;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.util.HttpUtil;
import com.example.coolweather.util.Utility;

import org.litepal.LitePal;
import org.litepal.crud.LitePalSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();

    private List<Province> provinceList;  // 省列表
    private List<City> cityList;
    private List<County> countyList;

    private Province selectedProvince;  // 选中的省份
    private  City selectedCity;
    private int currentLevel;  // 选中的级别

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        titleText = view.findViewById(R.id.title_text);
        backButton = view.findViewById(R.id.back_button);
        listView = view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dataList);  // ListView适配器
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                }
                else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {  // 返回操作
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY)
                    queryCities();
                else if (currentLevel == LEVEL_CITY)
                    queryProvinces();
            }
        });
        queryProvinces();
    }

    /**
     * 查询全国所有省，优先从数据库查询，若没有则去服务器上查询
     */
    private void queryProvinces() {
        titleText.setText("中国");  // 设置头布局标题
        backButton.setVisibility(View.GONE);  // 隐藏返回按钮
        provinceList = LitePal.findAll(Province.class);
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList)
                dataList.add(province.getProvinceName());
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }
        else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");
        }

    }

    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = LitePal.where("provinceid = ?", String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList)
                dataList.add(city.getCityName());
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }
        else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address, "city");
        }
    }

    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = LitePal.where("cityid = ?", String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList)
                dataList.add(county.getCountyName());
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        }
        else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "county");
        }
    }

    /**
     * 根据传入的地址和类型从服务器上查询省市县数据
     */
    private void queryFromServer(String address, final String type) {
        showProgressDialog();
        HttpUtil.sendOKHttpRequest(address, new Callback() {  // 向服务器发送请求
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {  // 回到主线程处理逻辑
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "Loading failed!",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {  // 服务器返回数据回调至此
                String responseText = response.body().string();
                boolean result = false;
                if ("province".equals(type))
                    result = Utility.handleProvinceResponse(responseText);  // 解析和处理服务器返回的数据，并存储到数据库中
                else if ("city".equals(type))
                    result = Utility.handleCityResponse(responseText, selectedProvince.getId());
                else if ("county".equals(type))
                    result = Utility.handleCountyResponse(responseText, selectedCity.getId());
                if (result) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type))
                                queryProvinces();  // 将数据显示在界面上
                            else if ("city".equals(type))
                                queryCities();
                            else if ("county".equals(type))
                                queryCounties();
                        }
                    });
                }
            }
        });
    }

    private void showProgressDialog() {  // 显示进度对话框
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("Loading...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    private void closeProgressDialog() {
        if (progressDialog != null)
            progressDialog.dismiss();
    }
}
