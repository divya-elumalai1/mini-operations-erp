import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { extractApiError, login as apiLogin } from '../api/endpoints'
import { useAuth } from '../lib/AuthContext'
import { Alert } from '../components/ui'

const credentialsSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
})

type Credentials = z.infer<typeof credentialsSchema>

export function LoginPage() {
  const { loginSuccess } = useAuth()
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<z.input<typeof credentialsSchema>, unknown, Credentials>({ resolver: zodResolver(credentialsSchema) })

  const mutation = useMutation({
    mutationFn: (credentials: Credentials) => apiLogin(credentials.username, credentials.password),
    onSuccess: (response) => {
      loginSuccess(response)
      navigate('/inventory')
    },
  })

  return (
    <div className="login-wrap">
      <form
        className="login-card"
        onSubmit={handleSubmit((values) => mutation.mutate(values))}
        aria-label="Log in"
      >
        <h1>Mini Operations ERP</h1>
        <p className="login-sub">Sign in with your operations account</p>

        {mutation.isError && (
          <Alert kind="error">{extractApiError(mutation.error).message ?? 'Login failed'}</Alert>
        )}

        <div className="field">
          <label htmlFor="username">Username</label>
          <input id="username" className="input" autoComplete="username" {...register('username')} />
          {errors.username && <span className="field-error">{errors.username.message}</span>}
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            className="input"
            autoComplete="current-password"
            {...register('password')}
          />
          {errors.password && <span className="field-error">{errors.password.message}</span>}
        </div>

        <button type="submit" className="btn" disabled={mutation.isPending} style={{ width: '100%' }}>
          {mutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>

        <div className="dev-hint">
          Development accounts (seeded):
          <br />
          <code>admin / Admin@123</code> · <code>ops / Ops@123</code> · <code>sales / Sales@123</code>
        </div>
      </form>
    </div>
  )
}
