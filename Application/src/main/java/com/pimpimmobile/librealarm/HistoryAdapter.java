package com.pimpimmobile.librealarm;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pimpimmobile.librealarm.shareddata.AlertRules;
import com.pimpimmobile.librealarm.shareddata.AlertRules.Danger;
import com.pimpimmobile.librealarm.shareddata.AlgorithmUtil;
import com.pimpimmobile.librealarm.shareddata.PredictionData;
import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss");
    private SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private List<PredictionData> mHistory;

    private LayoutInflater mInflater;

    private Context mContext;

    private OnListItemClickedListener mListener;

    public HistoryAdapter(Context context, OnListItemClickedListener listener) {
        mInflater = LayoutInflater.from(context);
        mContext = context;
        mListener = listener;
    }

    public void setHistory(List<PredictionData> history) {
        mHistory = history;
        if (mHistory != null) {
            Collections.sort(mHistory);
            Collections.reverse(mHistory);
        }
        JoH.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
       }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new HistoryViewHolder(mInflater.inflate(R.layout.history_item, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((HistoryViewHolder)holder).onBind(mHistory.get(position));
    }

    @Override
    public int getItemCount() {
        return mHistory == null ? 0 : mHistory.size();
    }

    protected class HistoryViewHolder extends RecyclerView.ViewHolder {

        TextView mTimeView;
        TextView mDateView;
        TextView mGlucoseView;
        ImageView mTrendArrow;

        public HistoryViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        int position = getAdapterPosition();
                        if (position != -1) mListener.onAdapterItemClicked(mHistory.get(position));

                    }
                }
            });
            mTimeView = (TextView) itemView.findViewById(R.id.time);
            mDateView = (TextView) itemView.findViewById(R.id.date);
            mGlucoseView = (TextView) itemView.findViewById(R.id.glucose);
            mTrendArrow = (ImageView) itemView.findViewById(R.id.trend_arrow);
        }

        private void onBind(PredictionData data) {
            Danger danger = AlertRules.checkDontPostpone(mContext, data);
            boolean error = data.errorCode != PredictionData.Result.OK;
            mGlucoseView.setTextColor(error ? Color.YELLOW :
                    (danger != Danger.NOTHING ? Color.RED : Color.WHITE));
            boolean isMmol = PreferencesUtil.getBoolean(mContext, mContext.getString(R.string.pref_key_mmol), true);
            if (error) {
                mGlucoseView.setText(R.string.err);
                mTrendArrow.setImageDrawable(null);
            } else {
                mGlucoseView.setText(String.valueOf(data.glucose(isMmol)));
                updateTrendArrow(data);
            }

            mTimeView.setText(mTimeFormat.format(new Date(data.realDate)));
            mDateView.setText(mDateFormat.format(new Date(data.realDate)));
        }

        private void updateTrendArrow(PredictionData data) {
            int drawableResource = getTrendDrawable(AlgorithmUtil.getTrendArrow(data));
            if (drawableResource == -1 || data.glucoseLevel < 0 || data.glucoseLevel == 0) {
                mGlucoseView.setTextColor(Color.MAGENTA);
                mGlucoseView.setText(R.string.s_err); // Set sensor error when no trend arrow on display, check negative and is zero values
                mTrendArrow.setImageDrawable(null);
            } else {
                mTrendArrow.setImageResource(drawableResource);
            }
        }
    }

    public static int getTrendDrawable(AlgorithmUtil.TrendArrow arrow) {
        switch (arrow) {
            case UP:
                return R.drawable.ic_arrow_upward_white_24dp;
            case DOWN:
                return R.drawable.ic_arrow_downward_white_24dp;
            case FLAT:
                return R.drawable.ic_arrow_forward_white_24dp;
            case SLIGHTLY_DOWN:
                return R.drawable.ic_arrow_slight_down_white_24dp;
            case SLIGHTLY_UP:
                return R.drawable.ic_arrow_slight_up_white_24dp;
        }
        return -1;
    }

    interface OnListItemClickedListener {
        void onAdapterItemClicked(PredictionData prediction);
    }
}
