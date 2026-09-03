import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  Svg: React.ComponentType<React.ComponentProps<'svg'>>;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: '高约定，开箱即用',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        引入一个 starter 即可获得统一响应、全局异常处理、TraceId 链路等生产基础设施。
        业务代码继续使用普通的 Java / Spring 编程模型。
      </>
    ),
  },
  {
    title: '可插拔，可替换',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        每项能力都通过特性开关声明式启停，每个 SPI 都能用一个 <code>@Bean</code> 覆盖为你自己的实现。
        限流、幂等、锁、存储的默认实现都可替换为分布式版本。
      </>
    ),
  },
  {
    title: '安全默认，边界清晰',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        请求加解密、签名、防重放、安全响应头、SQL 防护、文件魔数校验内置且默认安全。
        框架负责基础设施，业务持有领域模型与认证。
      </>
    ),
  },
];

function Feature({title, Svg, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
