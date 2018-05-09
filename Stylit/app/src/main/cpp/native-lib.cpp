#include <jni.h>
#include <string>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/core.hpp>
#include <vector>
#include <math.h>
using namespace std;
using namespace cv;
Mat makeCTXiao(Mat target, Mat source, Mat result);
void GetTRS(Mat input, Mat& T, Mat& R, Mat& S);
void GetSRT(Mat input, Mat& T, Mat& R, Mat& S);
Mat AddChannel(Mat mat);
Mat RemoveChannel(Mat mat);

extern "C" {
JNIEXPORT jstring
JNICALL
Java_com_mobileapps_bao_stylit_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    cv::Rect();
    cv::Mat();
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT jstring
JNICALL
Java_com_mobileapps_bao_stylit_MainActivity_stringFromJNI2(
        JNIEnv *env,
        jobject /* this */) {
    cv::Rect();
    cv::Mat();
    std::string hello = "Hello 2 from C++";
    return env->NewStringUTF(hello.c_str());
}
}extern "C"
JNIEXPORT void JNICALL
Java_com_mobileapps_bao_stylit_EditingModule_xiaoTransfer(JNIEnv* env, jobject instance, jlong target,jlong source, jlong result) {

    cv::Mat* inMatTarget = (cv::Mat*)target;
    cv::Mat* inMatSource = (cv::Mat*)source;
    cv::Mat* outMat = (cv::Mat*)result;
    *outMat = makeCTXiao(*inMatTarget, *inMatSource, *outMat);

}

Mat makeCTXiao(Mat target, Mat source, Mat res)
{
    Mat imgs = source;
    Mat imgt = target;
    cv::cvtColor(imgs,imgs,COLOR_RGB2BGR);
    cv::cvtColor(imgt,imgt,COLOR_RGB2BGR);
    imgs.convertTo(imgs, CV_64FC3, 1 / 255.0);
    imgt.convertTo(imgt, CV_64FC3, 1 / 255.0);
    Mat src_T, src_R, src_S;
    Mat tar_T, tar_R, tar_S;
    GetTRS(imgs, src_T, src_R, src_S);
    GetSRT(imgt, tar_T, tar_R, tar_S);
    Mat mega = src_T * src_R * src_S * tar_S * tar_R * tar_T;

    Mat img_4 = AddChannel(imgt);

    Mat result;
    //transpose(mega, mega);
    transform(img_4, result, mega);
    //std::cout << result.at<Vec4d>(0, 0) << std::endl; // for original method it has enormous values for CV_64F. If should be from 0 to 1.0
    //result = RemoveChannel(result);
    result.convertTo(result, CV_8UC3, 255);
    return result;
}
void GetTRS(Mat input, Mat& T, Mat& R, Mat& S)
{
    Mat cov, means;
    calcCovarMatrix(input.reshape(1, input.cols * input.rows), cov, means, CV_COVAR_NORMAL | CV_COVAR_ROWS, CV_64F);
    Mat U, A, VT;
    SVD::compute(cov, A, U, VT);
    T = Mat::eye(4, 4, CV_64FC1);
    R = Mat::eye(4, 4, CV_64FC1);
    S = Mat::eye(4, 4, CV_64FC1);

    Rect roi(0, 0, 3, 3);
    U.copyTo(R(roi));

    T.at<double>(0, 3) = means.at<double>(0, 0);
    T.at<double>(1, 3) = means.at<double>(0, 1);
    T.at<double>(2, 3) = means.at<double>(0, 2);

    // in original paper there is no sqrt()
    S.at<double>(0, 0) = sqrt(A.at<double>(0, 0));
    S.at<double>(1, 1) = sqrt(A.at<double>(1, 0));
    S.at<double>(2, 2) = sqrt(A.at<double>(2, 0));
}
void GetSRT(Mat input, Mat& T, Mat& R, Mat& S)
{
    Mat cov, means;
    calcCovarMatrix(input.reshape(1, input.cols * input.rows), cov, means, CV_COVAR_NORMAL | CV_COVAR_ROWS, CV_64F);
    Mat U, A, VT;
    SVD::compute(cov, A, U, VT);
    T = Mat::eye(4, 4, CV_64FC1);
    R = Mat::eye(4, 4, CV_64FC1);
    S = Mat::eye(4, 4, CV_64FC1);
    Rect roi(0, 0, 3, 3);
    invert(U, R(roi));

    T.at<double>(0, 3) = -means.at<double>(0, 0);
    T.at<double>(1, 3) = -means.at<double>(0, 1);
    T.at<double>(2, 3) = -means.at<double>(0, 2);

    S.at<double>(0, 0) = 1/sqrt(A.at<double>(0, 0));
    S.at<double>(1, 1) = 1/sqrt(A.at<double>(1, 0));
    S.at<double>(2, 2) = 1/sqrt(A.at<double>(2, 0));
}
Mat AddChannel(Mat mat)
{
    /*Mat img = Mat::ones(mat.size(), CV_64FC4);
    int from_to[] = {0,0, 1,1, 2,2};
    mixChannels(mat, img, from_to, 3);*/
    Mat img = Mat::ones(mat.size(), CV_64FC1);
    std::vector<Mat> channels;
    split(mat, channels);
    channels.push_back(img);
    merge(channels, img);
    return img;
}
Mat RemoveChannel(Mat mat)
{
    std::vector<Mat> channels;
    split(mat, channels);
    channels.resize(3);
    Mat img;
    merge(channels, img);
    return img;
}