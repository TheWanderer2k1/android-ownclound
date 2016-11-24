/**
 *   ownCloud Android client application
 *
 *   @author Bartek Przybylski
 *   @author David A. Velasco
 *   Copyright (C) 2012  Bartek Przybylski
 *   Copyright (C) 2016 ownCloud GmbH.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.datamodel;


import java.io.File;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.FileProvider;
import android.webkit.MimeTypeMap;

import com.owncloud.android.R;
import com.owncloud.android.lib.common.network.WebdavUtils;
import com.owncloud.android.lib.common.utils.Log_OC;

import third_parties.daveKoeller.AlphanumComparator;

public class OCFile implements Parcelable, Comparable<OCFile> {

    public static final Parcelable.Creator<OCFile> CREATOR = new Parcelable.Creator<OCFile>() {

        @Override
        public OCFile createFromParcel(Parcel source) {
            return new OCFile(source);
        }

        @Override
        public OCFile[] newArray(int size) {
            return new OCFile[size];
        }
    };

    private final static String PERMISSION_SHARED_WITH_ME = "S";    // TODO move to better location

    public static final String PATH_SEPARATOR = "/";
    public static final String ROOT_PATH = PATH_SEPARATOR;

    public enum AvailableOfflineStatus {

        /**
         * File is not available offline
         */
        NOT_AVAILABLE_OFFLINE(0),

        /**
         * File is available offline
         */
        AVAILABLE_OFFLINE(1),

        /**
         * File belongs to an available offline folder
         */
        AVAILABLE_OFFLINE_PARENT(2);

        private final int value;

        AvailableOfflineStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static AvailableOfflineStatus fromValue(int value) {
            switch (value) {
                case 0:
                    return NOT_AVAILABLE_OFFLINE;
                case 1:
                    return AVAILABLE_OFFLINE;
                case 2:
                    return AVAILABLE_OFFLINE_PARENT;
            }
            return null;
        }

    }

    private static final String TAG = OCFile.class.getSimpleName();

    private long mId;
    private long mParentId;
    private long mLength;
    private long mCreationTimestamp;
    private long mModifiedTimestamp;
    private long mModifiedTimestampAtLastSyncForData;
    private String mRemotePath;
    private String mLocalPath;
    private String mMimeType;
    private boolean mNeedsUpdating;
    private long mLastSyncDateForProperties;
    private long mLastSyncDateForData;
    private AvailableOfflineStatus mAvailableOfflineStatus;

    private String mEtag;

    private boolean mShareByLink;
    private String mPublicLink;

    private String mPermissions;
    private String mRemoteId;

    private boolean mNeedsUpdateThumbnail;

    private boolean mIsDownloading;

    private String mEtagInConflict;    // Save file etag in the server, when there is a conflict. No conflict =  null

    private boolean mShareWithSharee;

    /**
     * URI to the local path of the file contents, if stored in the device; cached after first call
     * to {@link #getStorageUri()}
     */
    private Uri mLocalUri;


    /**
     * Exportable URI to the local path of the file contents, if stored in the device.
     *
     * Cached after first call, until changed.
     */
    private Uri mExposedFileUri;


    /**
     * Create new {@link OCFile} with given path.
     * <p/>
     * The path received must be URL-decoded. Path separator must be OCFile.PATH_SEPARATOR, and it must be the first character in 'path'.
     *
     * @param path The remote path of the file.
     */
    public OCFile(String path) {
        resetData();
        mNeedsUpdating = false;
        if (path == null || path.length() <= 0 || !path.startsWith(PATH_SEPARATOR)) {
            throw new IllegalArgumentException("Trying to create a OCFile with a non valid remote path: " + path);
        }
        mRemotePath = path;
    }

    /**
     * Reconstruct from parcel
     *
     * @param source The source parcel
     */
    private OCFile(Parcel source) {
        mId = source.readLong();
        mParentId = source.readLong();
        mLength = source.readLong();
        mCreationTimestamp = source.readLong();
        mModifiedTimestamp = source.readLong();
        mModifiedTimestampAtLastSyncForData = source.readLong();
        mRemotePath = source.readString();
        mLocalPath = source.readString();
        mMimeType = source.readString();
        mNeedsUpdating = source.readInt() == 0;
        try {
            mAvailableOfflineStatus = AvailableOfflineStatus.valueOf(source.readString());
        } catch (IllegalArgumentException x) {
            mAvailableOfflineStatus = AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE;
        }
        mLastSyncDateForProperties = source.readLong();
        mLastSyncDateForData = source.readLong();
        mEtag = source.readString();
        mShareByLink = source.readInt() == 1;
        mPublicLink = source.readString();
        mPermissions = source.readString();
        mRemoteId = source.readString();
        mNeedsUpdateThumbnail = source.readInt() == 1;
        mIsDownloading = source.readInt() == 1;
        mEtagInConflict = source.readString();
        mShareWithSharee = source.readInt() == 1;

    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeLong(mParentId);
        dest.writeLong(mLength);
        dest.writeLong(mCreationTimestamp);
        dest.writeLong(mModifiedTimestamp);
        dest.writeLong(mModifiedTimestampAtLastSyncForData);
        dest.writeString(mRemotePath);
        dest.writeString(mLocalPath);
        dest.writeString(mMimeType);
        dest.writeInt(mNeedsUpdating ? 1 : 0);
        dest.writeString(mAvailableOfflineStatus.name());
        dest.writeLong(mLastSyncDateForProperties);
        dest.writeLong(mLastSyncDateForData);
        dest.writeString(mEtag);
        dest.writeInt(mShareByLink ? 1 : 0);
        dest.writeString(mPublicLink);
        dest.writeString(mPermissions);
        dest.writeString(mRemoteId);
        dest.writeInt(mNeedsUpdateThumbnail ? 1 : 0);
        dest.writeInt(mIsDownloading ? 1 : 0);
        dest.writeString(mEtagInConflict);
        dest.writeInt(mShareWithSharee ? 1 : 0);
    }

    /**
     * Gets the ID of the file
     *
     * @return the file ID
     */
    public long getFileId() {
        return mId;
    }

    /**
     * Returns the remote path of the file on ownCloud
     *
     * @return The remote path to the file
     */
    public String getRemotePath() {
        return mRemotePath;
    }

    /**
     * Can be used to check, whether or not this file exists in the database
     * already
     *
     * @return true, if the file exists in the database
     */
    public boolean fileExists() {
        return mId != -1;
    }

    /**
     * Use this to find out if this file is a folder.
     *
     * @return true if it is a folder
     */
    public boolean isFolder() {
        return mMimeType != null && mMimeType.equals("DIR");
    }

    /**
     * Use this to check if this file is available locally
     *
     * @return true if it is
     */
    public boolean isDown() {
        if (mLocalPath != null && mLocalPath.length() > 0) {
            File file = new File(mLocalPath);
            return (file.exists());
        }
        return false;
    }

    /**
     * The path, where the file is stored locally
     *
     * @return The local path to the file
     */
    public String getStoragePath() {
        return mLocalPath;
    }

    /**
     * The URI to the file contents, if stored locally
     *
     * @return A URI to the local copy of the file, or NULL if not stored in the device
     */
    public Uri getStorageUri() {
        if (mLocalPath == null || mLocalPath.length() == 0) {
            return null;
        }
        if (mLocalUri == null) {
            Uri.Builder builder = new Uri.Builder();
            builder.scheme(ContentResolver.SCHEME_FILE);
            builder.path(mLocalPath);
            mLocalUri = builder.build();
        }
        return mLocalUri;
    }

    public Uri getExposedFileUri(Context context) {
        if (mLocalPath == null || mLocalPath.length() == 0) {
            return null;
        }
        if (mExposedFileUri == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                // TODO - use FileProvider with any Android version, with deeper testing -> 2.2.0
                mExposedFileUri = Uri.parse(
                    ContentResolver.SCHEME_FILE + "://" + WebdavUtils.encodePath(mLocalPath)
                );
            } else {
                // Use the FileProvider to get a content URI
                try {
                    mExposedFileUri = FileProvider.getUriForFile(
                        context,
                        context.getString(R.string.file_provider_authority),
                        new File(mLocalPath)
                    );
                } catch (IllegalArgumentException e) {
                    Log_OC.e(TAG, "File can't be exported");
                }
            }
        }
        return mExposedFileUri;
    }

    /**
     * Can be used to set the path where the file is stored
     *
     * @param storage_path to set
     */
    public void setStoragePath(String storage_path) {
        mLocalPath = storage_path;
        mLocalUri = null;
        mExposedFileUri = null;
    }

    /**
     * Get a UNIX timestamp of the file creation time
     *
     * @return A UNIX timestamp of the time that file was created
     */
    public long getCreationTimestamp() {
        return mCreationTimestamp;
    }

    /**
     * Set a UNIX timestamp of the time the file was created
     *
     * @param creation_timestamp to set
     */
    public void setCreationTimestamp(long creation_timestamp) {
        mCreationTimestamp = creation_timestamp;
    }

    /**
     * Get a UNIX timestamp of the file modification time.
     *
     * @return A UNIX timestamp of the modification time, corresponding to the value returned by the server
     * in the last synchronization of the properties of this file.
     */
    public long getModificationTimestamp() {
        return mModifiedTimestamp;
    }

    /**
     * Set a UNIX timestamp of the time the time the file was modified.
     * <p/>
     * To update with the value returned by the server in every synchronization of the properties
     * of this file.
     *
     * @param modification_timestamp to set
     */
    public void setModificationTimestamp(long modification_timestamp) {
        mModifiedTimestamp = modification_timestamp;
    }


    /**
     * Get a UNIX timestamp of the file modification time.
     *
     * @return A UNIX timestamp of the modification time, corresponding to the value returned by the server
     * in the last synchronization of THE CONTENTS of this file.
     */
    public long getModificationTimestampAtLastSyncForData() {
        return mModifiedTimestampAtLastSyncForData;
    }

    /**
     * Set a UNIX timestamp of the time the time the file was modified.
     * <p/>
     * To update with the value returned by the server in every synchronization of THE CONTENTS
     * of this file.
     *
     * @param modificationTimestamp to set
     */
    public void setModificationTimestampAtLastSyncForData(long modificationTimestamp) {
        mModifiedTimestampAtLastSyncForData = modificationTimestamp;
    }


    /**
     * Returns the filename and "/" for the root directory
     *
     * @return The name of the file
     */
    public String getFileName() {
        File f = new File(getRemotePath());
        return f.getName().length() == 0 ? ROOT_PATH : f.getName();
    }

    /**
     * Sets the name of the file
     * <p/>
     * Does nothing if the new name is null, empty or includes "/" ; or if the file is the root
     * directory
     */
    public void setFileName(String name) {
        Log_OC.d(TAG, "OCFile name changin from " + mRemotePath);
        if (name != null && name.length() > 0 && !name.contains(PATH_SEPARATOR) &&
                !mRemotePath.equals(ROOT_PATH)) {
            String parent = (new File(getRemotePath())).getParent();
            parent = (parent.endsWith(PATH_SEPARATOR)) ? parent : parent + PATH_SEPARATOR;
            mRemotePath = parent + name;
            if (isFolder()) {
                mRemotePath += PATH_SEPARATOR;
            }
            Log_OC.d(TAG, "OCFile name changed to " + mRemotePath);
        }
    }

    /**
     * Can be used to get the Mimetype
     *
     * @return the Mimetype as a String
     */
    public String getMimetype() {
        return mMimeType;
    }

    /**
     * Used internally. Reset all file properties
     */
    private void resetData() {
        mId = -1;
        mRemotePath = null;
        mParentId = 0;
        mLocalPath = null;
        mMimeType = null;
        mLength = 0;
        mCreationTimestamp = 0;
        mModifiedTimestamp = 0;
        mModifiedTimestampAtLastSyncForData = 0;
        mLastSyncDateForProperties = 0;
        mLastSyncDateForData = 0;
        mAvailableOfflineStatus = AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE;
        mNeedsUpdating = false;
        mEtag = null;
        mShareByLink = false;
        mPublicLink = null;
        mPermissions = null;
        mRemoteId = null;
        mNeedsUpdateThumbnail = false;
        mIsDownloading = false;
        mEtagInConflict = null;
        mShareWithSharee = false;
    }

    /**
     * Sets the ID of the file
     *
     * @param file_id to set
     */
    public void setFileId(long file_id) {
        mId = file_id;
    }

    /**
     * Sets the Mime-Type of the
     *
     * @param mimetype to set
     */
    public void setMimetype(String mimetype) {
        mMimeType = mimetype;
    }

    /**
     * Sets the ID of the parent folder
     *
     * @param parent_id to set
     */
    public void setParentId(long parent_id) {
        mParentId = parent_id;
    }

    /**
     * Sets the file size in bytes
     *
     * @param file_len to set
     */
    public void setFileLength(long file_len) {
        mLength = file_len;
    }

    /**
     * Returns the size of the file in bytes
     *
     * @return The filesize in bytes
     */
    public long getFileLength() {
        return mLength;
    }

    /**
     * Returns the ID of the parent Folder
     *
     * @return The ID
     */
    public long getParentId() {
        return mParentId;
    }

    /**
     * get remote path of parent file
     * @return remote path
     */
    public String getParentRemotePath() {
        String parentPath = new File(getRemotePath()).getParent();
        return (parentPath.endsWith("/")) ? parentPath : (parentPath + "/");
    }

    /**
     * Check, if this file needs updating
     *
     * @return
     */
    public boolean needsUpdatingWhileSaving() {
        return mNeedsUpdating;
    }

    public boolean needsUpdateThumbnail() {
        return mNeedsUpdateThumbnail;
    }

    public void setNeedsUpdateThumbnail(boolean needsUpdateThumbnail) {
        this.mNeedsUpdateThumbnail = needsUpdateThumbnail;
    }

    public long getLastSyncDateForProperties() {
        return mLastSyncDateForProperties;
    }

    public void setLastSyncDateForProperties(long lastSyncDate) {
        mLastSyncDateForProperties = lastSyncDate;
    }

    public long getLastSyncDateForData() {
        return mLastSyncDateForData;
    }

    public void setLastSyncDateForData(long lastSyncDate) {
        mLastSyncDateForData = lastSyncDate;
    }

    public void setAvailableOfflineStatus(AvailableOfflineStatus availableOffline) {
        mAvailableOfflineStatus = availableOffline;
    }

    public AvailableOfflineStatus getAvailableOfflineStatus() {
        return mAvailableOfflineStatus;
    }

    /**
     * @return      'True' when
     */
    public boolean isAvailableOffline() {
        return (mAvailableOfflineStatus != AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE);
    }

    @Override
    public int describeContents() {
        return super.hashCode();
    }

    @Override
    public int compareTo(OCFile another) {
        if (isFolder() && another.isFolder()) {
            return getRemotePath().toLowerCase().compareTo(another.getRemotePath().toLowerCase());
        } else if (isFolder()) {
            return -1;
        } else if (another.isFolder()) {
            return 1;
        }
        return new AlphanumComparator().compare(this, another);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof OCFile) {
            OCFile that = (OCFile) o;
            if (that != null) {
                return this.mId == that.mId;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        String asString = "[id=%s, name=%s, mime=%s, downloaded=%s, local=%s, remote=%s, " +
                "parentId=%s, favorite=%s etag=%s]";
        asString = String.format(asString, Long.valueOf(mId), getFileName(), mMimeType, isDown(),
                mLocalPath, mRemotePath, Long.valueOf(mParentId), mAvailableOfflineStatus,
                mEtag);
        return asString;
    }

    public String getEtag() {
        return mEtag;
    }

    public void setEtag(String etag) {
        this.mEtag = (etag != null ? etag : "");
    }


    public boolean isSharedViaLink() {
        return mShareByLink;
    }

    public void setShareViaLink(boolean shareByLink) {
        this.mShareByLink = shareByLink;
    }

    public String getPublicLink() {
        return mPublicLink;
    }

    public void setPublicLink(String publicLink) {
        this.mPublicLink = publicLink;
    }

    public long getLocalModificationTimestamp() {
        if (mLocalPath != null && mLocalPath.length() > 0) {
            File f = new File(mLocalPath);
            return f.lastModified();
        }
        return 0;
    }

    /**
     * @return 'True' if the file contains audio
     */
    public boolean isAudio() {
        return isOfType("audio/");
    }

    /**
     * @return 'True' if the file contains video
     */
    public boolean isVideo() {
        return isOfType("video/");
    }

    /**
     * @return 'True' if the file contains an image
     */
    public boolean isImage() {
        return isOfType("image/");
    }

    /**
     * @return 'True' if the file is simple text (e.g. not application-dependent, like .doc or .docx)
     */
    public boolean isText() {
        return isOfType("text/");
    }

    /**
     * @param   type        Type to match in the file MIME type; it's MUST include the trailing "/"
     * @return              'True' if the file MIME type matches the received parameter in the type part.
     */
    private boolean isOfType(String type) {
        return (
            (mMimeType != null && mMimeType.startsWith(type)) ||
            getMimeTypeFromName().startsWith(type)
        );
    }

    public String getMimeTypeFromName() {
        String extension = "";
        int pos = mRemotePath.lastIndexOf('.');
        if (pos >= 0) {
            extension = mRemotePath.substring(pos + 1);
        }
        String result = MimeTypeMap.getSingleton().
                getMimeTypeFromExtension(extension.toLowerCase());
        return (result != null) ? result : "";
    }

    /**
     * @return 'True' if the file is hidden
     */
    public boolean isHidden() {
        return getFileName().startsWith(".");
    }

    public String getPermissions() {
        return mPermissions;
    }

    public void setPermissions(String permissions) {
        this.mPermissions = permissions;
    }

    public String getRemoteId() {
        return mRemoteId;
    }

    public void setRemoteId(String remoteId) {
        this.mRemoteId = remoteId;
    }

    public boolean isDownloading() {
        return mIsDownloading;
    }

    public void setDownloading(boolean isDownloading) {
        this.mIsDownloading = isDownloading;
    }

    public String getEtagInConflict() {
        return mEtagInConflict;
    }

    public boolean isInConflict() {
        return mEtagInConflict != null && mEtagInConflict != "";
    }

    public void setEtagInConflict(String etagInConflict) {
        mEtagInConflict = etagInConflict;
    }

    public boolean isSharedWithSharee() {
        return mShareWithSharee;
    }

    public void setShareWithSharee(boolean shareWithSharee) {
        this.mShareWithSharee = shareWithSharee;
    }

    public boolean isSharedWithMe() {
        String permissions = getPermissions();
        return (permissions != null && permissions.contains(PERMISSION_SHARED_WITH_ME));
    }

    public void copyLocalPropertiesFrom(OCFile sourceFile) {
        setParentId(sourceFile.getParentId());
        setFileId(sourceFile.getFileId());
        setAvailableOfflineStatus(sourceFile.getAvailableOfflineStatus());
        setLastSyncDateForData(sourceFile.getLastSyncDateForData());
        setModificationTimestampAtLastSyncForData(
            sourceFile.getModificationTimestampAtLastSyncForData()
        );
        setStoragePath(sourceFile.getStoragePath());
        setPublicLink(sourceFile.getPublicLink());
        setShareViaLink(sourceFile.isSharedViaLink());
        setShareWithSharee(sourceFile.isSharedWithSharee());
        setEtagInConflict(sourceFile.getEtagInConflict());
    }

}
