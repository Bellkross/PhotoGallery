package com.bignerdranch.android.photogallery;


import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private GridLayoutManager mGridLayoutManager;
    private int mLastPosition;
    private int mFirstPosition;
    private boolean mUserScrolled;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        new FetchItemsTask().execute();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener((photoHolder, bitmap) -> {
                    Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                    photoHolder.bindDrawable(drawable);
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.getViewTreeObserver().
                addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        float columnWidthInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                140, getActivity().getResources().getDisplayMetrics());
                        int width = mPhotoRecyclerView.getWidth();
                        int columnNumber = Math.round(width / columnWidthInPixels);
                        mGridLayoutManager = new GridLayoutManager(getActivity(), columnNumber);
                        mPhotoRecyclerView.setLayoutManager(mGridLayoutManager);
                        mPhotoRecyclerView.scrollToPosition(mFirstPosition);
                        mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPhotoRecyclerView.setOnScrollChangeListener((v1, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                mFirstPosition = mGridLayoutManager.findFirstVisibleItemPosition();
                mLastPosition = mGridLayoutManager.findLastVisibleItemPosition();
                if (!mUserScrolled) {
                    if (mLastPosition == mItems.size() - 1) {
                        mUserScrolled = true;
                        Toast.makeText(getActivity(), "Bottom" + mLastPosition, Toast.LENGTH_SHORT).show();
                        new FetchItemsTask().execute();
                    }
                }

            });
        }
        setupAdapter();
        return v;
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {

            int top = (mFirstPosition > 10) ? mFirstPosition - 10 : 0;
            int bottom = mLastPosition > mGalleryItems.size() - 11 ?
                    mGalleryItems.size() : mLastPosition + 10;

            GalleryItem galleryItem;
            Drawable placeholder;
            placeholder = getResources().getDrawable(R.drawable.bill_up_close);
            photoHolder.bindDrawable(placeholder);

            if (mLastPosition >= 14) {
                for (int i = mFirstPosition; i <= top; i++) {
                    galleryItem = mGalleryItems.get(i);
                    mThumbnailDownloader.queueThumbnailCache(photoHolder, galleryItem.getUrl());
                }
            }

            for (int i = mLastPosition; i < bottom; i++) {
                galleryItem = mGalleryItems.get(i);
                mThumbnailDownloader.queueThumbnailCache(photoHolder, galleryItem.getUrl());
            }


            galleryItem = mGalleryItems.get(position);
            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
            return new FlickrFetchr().fetchItems();
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            for (GalleryItem item : items) {
                mItems.add(item);
            }
            mUserScrolled = false;
            setupAdapter();
            mPhotoRecyclerView.scrollToPosition(mFirstPosition);
        }
    }
}
