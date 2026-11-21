package com.lighttodo.app.model;

import com.lighttodo.app.R;

/** 四象限分类：重要紧急 / 重要不紧急 / 不重要紧急 / 不重要不紧急。 */
public final class Category {

    public static final int IMPORTANT_URGENT = 0;
    public static final int IMPORTANT_NOT_URGENT = 1;
    public static final int NOT_IMPORTANT_URGENT = 2;
    public static final int NOT_IMPORTANT_NOT_URGENT = 3;

    public static final int COUNT = 4;

    private Category() {
    }

    /** 由「是否重要」「是否紧急」两个标记推出分类。 */
    public static int of(boolean important, boolean urgent) {
        if (important && urgent) return IMPORTANT_URGENT;
        if (important) return IMPORTANT_NOT_URGENT;
        if (urgent) return NOT_IMPORTANT_URGENT;
        return NOT_IMPORTANT_NOT_URGENT;
    }

    public static int of(Task task) {
        return of(task.important, task.urgent);
    }

    /** 分类对应的强调色资源。 */
    public static int colorRes(int category) {
        switch (category) {
            case IMPORTANT_URGENT:
                return R.color.cat_important_urgent;
            case IMPORTANT_NOT_URGENT:
                return R.color.cat_important_not_urgent;
            case NOT_IMPORTANT_URGENT:
                return R.color.cat_not_important_urgent;
            default:
                return R.color.cat_not_important_not_urgent;
        }
    }

    /** 分类对应的小圆点图片（小组件里用，运行时不好换色，所以每类一个）。 */
    public static int dotRes(int category) {
        switch (category) {
            case IMPORTANT_URGENT:
                return R.drawable.w_dot_important_urgent;
            case IMPORTANT_NOT_URGENT:
                return R.drawable.w_dot_important_not_urgent;
            case NOT_IMPORTANT_URGENT:
                return R.drawable.w_dot_not_important_urgent;
            default:
                return R.drawable.w_dot_not_important_not_urgent;
        }
    }

    /** 分类对应的小圆环（小组件里表示紧急程度：红=重要紧急 …）。 */
    public static int ringRes(int category) {
        switch (category) {
            case IMPORTANT_URGENT:
                return R.drawable.w_ic_todo_important_urgent;
            case IMPORTANT_NOT_URGENT:
                return R.drawable.w_ic_todo_important_not_urgent;
            case NOT_IMPORTANT_URGENT:
                return R.drawable.w_ic_todo_not_important_urgent;
            default:
                return R.drawable.w_ic_todo_not_important_not_urgent;
        }
    }

    /** 分类名对应的字符串资源。 */
    public static int nameRes(int category) {
        switch (category) {
            case IMPORTANT_URGENT:
                return R.string.cat_important_urgent;
            case IMPORTANT_NOT_URGENT:
                return R.string.cat_important_not_urgent;
            case NOT_IMPORTANT_URGENT:
                return R.string.cat_not_important_urgent;
            default:
                return R.string.cat_not_important_not_urgent;
        }
    }

    /** 分类的一句话解释。 */
    public static int descRes(int category) {
        switch (category) {
            case IMPORTANT_URGENT:
                return R.string.cat_important_urgent_desc;
            case IMPORTANT_NOT_URGENT:
                return R.string.cat_important_not_urgent_desc;
            case NOT_IMPORTANT_URGENT:
                return R.string.cat_not_important_urgent_desc;
            default:
                return R.string.cat_not_important_not_urgent_desc;
        }
    }
}
