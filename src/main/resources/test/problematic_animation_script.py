from manim import *

class ManimScene(Scene):
    def construct(self):
        # 介绍特征值和特征向量
        title = Tex(r"特征值和特征向量的几何意义", tex_template=TexTemplateLibrary.ctex)
        title.scale(1.2).to_edge(UP)
        self.play(Write(title))
        self.wait(1)
        
        # 创建坐标系
        axes = Axes(
            x_range=[-5, 5, 1],
            y_range=[-5, 5, 1],
            axis_config={"color": BLUE},
            x_length=8,
            y_length=8
        )
        axes_labels = axes.get_axis_labels(x_label="x", y_label="y")
        
        # 展示坐标系
        self.play(Create(axes), Write(axes_labels))
        self.wait(1)
        
        # 解释特征值和特征向量
        explanation = Tex(r"特征向量是经过线性变换后方向不变的向量", tex_template=TexTemplateLibrary.ctex)
        explanation.to_edge(DOWN).shift(UP)
        self.play(Write(explanation))
        self.wait(2)
        
        # 创建矩阵A
        matrix_text = Tex(r"矩阵 $A = \begin{bmatrix} 3 & 1 \\ 1 & 3 \end{bmatrix}$", tex_template=TexTemplateLibrary.ctex)
        matrix_text.to_edge(UP).shift(DOWN)
        self.play(Write(matrix_text))
        self.wait(1)
        
        # 计算特征值和特征向量
        eig_text1 = Tex(r"特征值 $\lambda_1 = 4$, 特征向量 $v_1 = \begin{bmatrix} 1 \\ 1 \end{bmatrix}$", tex_template=TexTemplateLibrary.ctex)
        eig_text2 = Tex(r"特征值 $\lambda_2 = 2$, 特征向量 $v_2 = \begin{bmatrix} -1 \\ 1 \end{bmatrix}$", tex_template=TexTemplateLibrary.ctex)
        
        eig_text1.next_to(matrix_text, DOWN, 0.5)
        eig_text2.next_to(eig_text1, DOWN, 0.5)
        
        self.play(Write(eig_text1))
        self.wait(1)
        self.play(Write(eig_text2))
        self.wait(1)
        
        # 显示特征向量
        eigvec1 = Arrow(axes.get_origin(), axes.c2p(1, 1), buff=0, color=RED)
        eigvec2 = Arrow(axes.get_origin(), axes.c2p(-1, 1), buff=0, color=GREEN)
        
        eigvec1_label = Tex(r"$v_1$").next_to(eigvec1.get_end(), UR*0.5)
        eigvec2_label = Tex(r"$v_2$").next_to(eigvec2.get_end(), UL*0.5)
        
        self.play(Create(eigvec1), Write(eigvec1_label))
        self.play(Create(eigvec2), Write(eigvec2_label))
        self.wait(1)
        
        # 说明特征向量的性质
        property_text = Tex(r"$Av_1 = \lambda_1 v_1$ 和 $Av_2 = \lambda_2 v_2$", tex_template=TexTemplateLibrary.ctex)
        property_text.to_edge(DOWN).shift(UP 1.5)
        self.play(Write(property_text))
        self.wait(2)
        
        # 演示矩阵A作用在特征向量上
        # 特征向量v1的变换
        scaled_eigvec1 = Arrow(axes.get_origin(), axes.c2p(4, 4), buff=0, color=RED_E)
        scaled_eigvec1_label = Tex(r"$Av_1 = 4v_1$").next_to(scaled_eigvec1.get_end(), UR*0.5)
        
        transform_text1 = Tex(r"$A\begin{bmatrix} 1 \\ 1 \end{bmatrix} = \begin{bmatrix} 3 & 1 \\ 1 & 3 \end{bmatrix}\begin{bmatrix} 1 \\ 1 \end{bmatrix} = \begin{bmatrix} 4 \\ 4 \end{bmatrix} = 4\begin{bmatrix} 1 \\ 1 \end{bmatrix}$")
        transform_text1.scale(0.7).next_to(eig_text2, DOWN, 1)
        
        self.play(Write(transform_text1))
        self.wait(1.5)
        self.play(
            Transform(eigvec1, scaled_eigvec1),
            Transform(eigvec1_label, scaled_eigvec1_label)
        )
        self.wait(1.5)
        
        # 特征向量v2的变换
        scaled_eigvec2 = Arrow(axes.get_origin(), axes.c2p(-2, 2), buff=0, color=GREEN_E)
        scaled_eigvec2_label = Tex(r"$Av_2 = 2v_2$").next_to(scaled_eigvec2.get_end(), UL*0.5)
        
        transform_text2 = Tex(r"$A\begin{bmatrix} -1 \\ 1 \end{bmatrix} = \begin{bmatrix} 3 & 1 \\ 1 & 3 \end{bmatrix}\begin{bmatrix} -1 \\ 1 \end{bmatrix} = \begin{bmatrix} -2 \\ 2 \end{bmatrix} = 2\begin{bmatrix} -1 \\ 1 \end{bmatrix}$")
        transform_text2.scale(0.7).next_to(transform_text1, DOWN, 0.8)
        
        self.play(Write(transform_text2))
        self.wait(1.5)
        self.play(
            Transform(eigvec2, scaled_eigvec2),
            Transform(eigvec2_label, scaled_eigvec2_label)
        )
        self.wait(1.5)
        
        # 演示普通向量的变换
        normal_vec = Arrow(axes.get_origin(), axes.c2p(2, 1), buff=0, color=YELLOW)
        normal_vec_label = Tex(r"$u = \begin{bmatrix} 2 \\ 1 \end{bmatrix}$").next_to(normal_vec.get_end(), RIGHT)
        
        self.play(Create(normal_vec), Write(normal_vec_label))
        self.wait(1)
        
        # 计算Au
        transform_text3 = Tex(r"$Au = A\begin{bmatrix} 2 \\ 1 \end{bmatrix} = \begin{bmatrix} 3 & 1 \\ 1 & 3 \end{bmatrix}\begin{bmatrix} 2 \\ 1 \end{bmatrix} = \begin{bmatrix} 7 \\ 5 \end{bmatrix}$")
        transform_text3.scale(0.7).next_to(transform_text2, DOWN, 0.8)
        
        self.play(Write(transform_text3))
        self.wait(1.5)
        
        # 变换后的向量
        transformed_vec = Arrow(axes.get_origin(), axes.c2p(7, 5), buff=0, color=YELLOW_E)
        transformed_vec_label = Tex(r"$Au = \begin{bmatrix} 7 \\ 5 \end{bmatrix}$").next_to(transformed_vec.get_end(), RIGHT)
        
        self.play(
            Transform(normal_vec, transformed_vec),
            Transform(normal_vec_label, transformed_vec_label)
        )
        self.wait(2)
        
        # 说明普通向量变换后方向改变
        direction_text = Tex(r"注意：普通向量在变换后方向发生了改变", tex_template=TexTemplateLibrary.ctex)
        direction_text.to_edge(DOWN).shift(UP)
        self.play(ReplacementTransform(property_text, direction_text))
        self.wait(2)
        
        # 结论
        conclusion = Tex(r"特征向量在矩阵变换后只改变大小，不改变方向\\\\特征值就是特征向量的伸缩系数", tex_template=TexTemplateLibrary.ctex)
        conclusion.to_edge(DOWN).shift(UP)
        self.play(ReplacementTransform(direction_text, conclusion))
        self.wait(2)
        
        # 淡出所有元素
        self.play(
            FadeOut(title),
            FadeOut(matrix_text),
            FadeOut(eig_text1),
            FadeOut(eig_text2),
            FadeOut(transform_text1),
            FadeOut(transform_text2),
            FadeOut(transform_text3),
            FadeOut(axes),
            FadeOut(axes_labels),
            FadeOut(eigvec1),
            FadeOut(eigvec2),
            FadeOut(eigvec1_label),
            FadeOut(eigvec2_label),
            FadeOut(normal_vec),
            FadeOut(normal_vec_label),
            FadeOut(conclusion)
        )
        self.wait(1)